package com.ibasco.ucgdisplay.tools;

import static com.ibasco.ucgdisplay.tools.util.StringUtils.sanitizeData;

import com.ibasco.ucgdisplay.tools.beans.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses the u8g2 codebuild.c file
 *
 * @author Rafael Ibasco
 * @see <a href="https://github.com/olikraus/u8g2/blob/master/tools/codebuild/codebuild.c">codebuild.c</a>
 */
public class CodeParser {

    private static final Logger log = LoggerFactory.getLogger(CodeParser.class);

    private static final Pattern PATTERN_EXTRACT_CONTROLLERS = Pattern.compile("struct\\s+controller\\s+controller\\_list\\[\\].+\\n\\{(?<controllers>(?s:.)+)\\}\\;");

    private static final Pattern PATTERN_PARSE_ENTRIES = Pattern.compile("(?<controller>\\{\\\"(?<name>[\\w\\d]*)\\\",(?<tilewidth>\\d*),(?<tileheight>\\d*),\\\"(?<hvline>[\\w\\d]*)\\\",\\\"(?<cad>[\\w\\d]*)\\\",\\\"(?<cadshort>[\\w\\d]*)\\\",(?<com>[\\w\\d\\|]*)?,\\\"(?<note>.*?)\\\",(?<flag>[\\d]*),(\\{(?<vendors>((\\{\\\"?[\\w\\d]*\\\"?\\}).*?)*?,?)\\})?).*?");

    private static final Pattern PATTERN_PARSE_DISPLAY = Pattern.compile("\\{\\\"?(?<display>.*?)\\\"?\\}");

    private static final Pattern PATTERN_CODE_COMMENTS = Pattern.compile("\\/\\*.+\\*\\/");

    private static final Pattern PATTERN_BLANK_LINES = Pattern.compile("(?m)^[ \t]*\n?\n");

    private static final Pattern PATTERN_INTERFACE = Pattern.compile("(?s)\\{.+?\\\"(?<interfaceName>.*?)\\\"[\\s\\t]*,.+?\\\"(?<setpinFunction>.*?)\\\"[\\s\\t]*,.+?\\\"(?<arduinoComProcedure>.*?)\\\"[\\s\\t]*,.+?\\\"(?<arduinoGpioProcedure>.*?)\\\"[\\s\\t]*,.+?\\\"(?<pinsWithType>.*?)\\\"[\\s\\t]*,.+?\\\"(?<pinsPlain>.*?)\\\"[\\s\\t]*,.+?\\\"(?<pinsMdPlain>.*?)\\\"[\\s\\t]*,.+?\\\"(?<genericComProcedure>.*?)\\\".+?\\}");

    public List<Controller> parseControllerCode(String code) {
        ArrayList<Controller> result;
        Matcher controllerMatcher = PATTERN_EXTRACT_CONTROLLERS.matcher(code);
        Map<String, Controller> controllerMap = new HashMap<>();

        while (controllerMatcher.find()) {
            String controllerGroup = sanitizeData(controllerMatcher.group("controllers"));
            Matcher entryMatcher = PATTERN_PARSE_ENTRIES.matcher(controllerGroup);

            //Start controller iteration
            while (entryMatcher.find()) {
                String name = entryMatcher.group("name").toUpperCase();
                int tileWidth = Integer.parseInt(entryMatcher.group("tilewidth"));
                int tileHeight = Integer.parseInt(entryMatcher.group("tileheight"));
                String bufferLayout = entryMatcher.group("hvline");
                String cad = entryMatcher.group("cad");
                String cadShort = entryMatcher.group("cadshort");
                String com = entryMatcher.group("com");
                String notes = entryMatcher.group("note");
                String flag = entryMatcher.group("flag");
                String vendors = entryMatcher.group("vendors");
                String[] vendorCodes = vendors.split(",");

                log.debug("name: {}, tile width = {}, tile height = {}, hvline = {}, cad = {}, cadshort = {}, com = {}, notes = {}, flag = {}, vendors = {}", name, tileWidth, tileHeight, bufferLayout, cad, cadShort, com, notes, flag, vendors);

                Controller controller = controllerMap.computeIfAbsent(name, s -> new Controller(name));

                for (var vendorCode : vendorCodes) {
                    String vendorName = parseVendorName(vendorCode).toUpperCase();

                    //Skip "null" vendors
                    if ("null".equalsIgnoreCase(vendorName))
                        continue;

                    var vendor = extractVendor(controller, vendorName);

                    //Create a new entry if not yet existing
                    if (vendor == null) {
                        //Create/update vendor properties
                        vendor = new Vendor(controller, vendorName.toUpperCase());
                        vendor.setTileWidth(tileWidth);
                        vendor.setTileHeight(tileHeight);
                        vendor.setNotes(notes);
                        vendor.setBufferLayout(bufferLayout);
                    }

                    //Update config
                    var vendorConfig = new VendorConfig();
                    vendorConfig.setVendor(vendor);
                    vendorConfig.setCadName(cad);
                    vendorConfig.setCadNameShort(cadShort);

                    for (var comm : Arrays.stream(com.split("\\|")).map(commName -> new Comm(commName)).collect(Collectors.toList())) {
                        comm.setValue(getCommValue(comm.getName()));
                        vendorConfig.getSupportedInterfaces().add(comm);
                    }

                    vendor.getVendorConfigs().add(vendorConfig);
                    controller.getVendorList().add(vendor);
                }
            }
        }

        result = new ArrayList<>(controllerMap.values());
        Collections.sort(result);

        log.debug("Found a total of {} controllers", result.size());
        return result;
    }

    public List<CommInterface> parseInterfaceCode(String code) {
        var interfaces = new ArrayList<CommInterface>();
        code = stripBlankLines(stripCodeComments(code));
        log.info("[PARSE-INTERFACE] Parsing comm interface code");
        Matcher interfaceMatcher = PATTERN_INTERFACE.matcher(code);
        int index = 0;
        while (interfaceMatcher.find()) {
            String name = interfaceMatcher.group("interfaceName");
            String setPinFunction = interfaceMatcher.group("setpinFunction");
            String arduinoComProc = interfaceMatcher.group("arduinoComProcedure");
            String arduinoGpioProc = interfaceMatcher.group("arduinoGpioProcedure");
            String pinsWithType = interfaceMatcher.group("pinsWithType");
            String pinsPlain = interfaceMatcher.group("pinsPlain");
            String pinsMdPlain = interfaceMatcher.group("pinsMdPlain");
            String genericComProc = interfaceMatcher.group("genericComProcedure");
            var commInterface = new CommInterface(index++, name, setPinFunction, arduinoComProc, arduinoGpioProc, pinsWithType, pinsPlain, pinsMdPlain, genericComProc);
            interfaces.add(commInterface);
            log.info("[PARSE-INTERFACE] Parsed Comm Interface = {}", commInterface);
        }
        log.info("[PARSE-INTERFACE] Parsed a total of {} interfaces", interfaces.size());
        return interfaces;
    }

    private String stripCodeComments(String code) {
        return code.replaceAll(PATTERN_CODE_COMMENTS.pattern(), "");
    }

    private String stripBlankLines(String code) {
        return code.replaceAll(PATTERN_BLANK_LINES.pattern(), "");
    }

    private int getCommValue(String comm) {
        switch (comm) {
            case "COM_4WSPI": {
                return 0x0001;
            }
            case "COM_3WSPI": {
                return 0x0002;
            }
            case "COM_6800": {
                return 0x0004;
            }
            case "COM_8080": {
                return 0x0008;
            }
            case "COM_I2C": {
                return 0x0010;
            }
            case "COM_ST7920SPI": {
                return 0x0020;
            }
            case "COM_UART": {
                return 0x0040;
            }
            case "COM_KS0108": {
                return 0x0080;
            }
            case "COM_SED1520": {
                return 0x0100;
            }
            default: {
                return -1;
            }
        }
    }

    private Vendor extractVendor(Controller controller, String vendorName) {
        return controller.getVendorList().stream()
                .filter(p -> p.getName().equalsIgnoreCase(vendorName))
                .findFirst()
                .orElse(null);
    }

    private String parseVendorName(String displayCode) {
        Matcher displayMatcher = PATTERN_PARSE_DISPLAY.matcher(displayCode);
        if (displayMatcher.find()) {
            String res = displayMatcher.group("display").trim().toLowerCase();
            return res.replaceAll("\"", "");
        }
        throw new IllegalStateException("Unable to extract vendor name from: " + displayCode);
    }
}
