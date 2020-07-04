package com.ibasco.ucgdisplay.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ibasco.ucgdisplay.drivers.glcd.GlcdDisplay;
import com.ibasco.ucgdisplay.drivers.glcd.GlcdInterfaceInfo;
import com.ibasco.ucgdisplay.drivers.glcd.GlcdInterfaceLookup;
import com.ibasco.ucgdisplay.drivers.glcd.GlcdSetupInfo;
import com.ibasco.ucgdisplay.drivers.glcd.enums.GlcdBufferLayout;
import com.ibasco.ucgdisplay.drivers.glcd.enums.GlcdController;
import com.ibasco.ucgdisplay.drivers.glcd.enums.GlcdSize;
import com.ibasco.ucgdisplay.tools.beans.*;
import com.ibasco.ucgdisplay.tools.service.GithubService;
import com.ibasco.ucgdisplay.tools.util.CodeBuilder;
import com.ibasco.ucgdisplay.tools.util.StringUtils;
import static com.ibasco.ucgdisplay.tools.util.StringUtils.formatVendorName;
import com.squareup.javapoet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates java and c++ code for ucgdisplay
 *
 * @author Rafael Ibasco
 */
public class CodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerator.class);

    private final GithubService githubService = new GithubService();

    private List<String> fontCache = new ArrayList<>();

    private List<String> u8g2FileCache = new ArrayList<>();

    private String lastFontBranch;

    private String lastU8g2Branch;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private boolean includeComments;

    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME;

    private CodeExtractor extractor;

    public CodeGenerator(CodeExtractor extractor) {
        this.extractor = extractor;
    }

    public boolean isIncludeComments() {
        return includeComments;
    }

    public void setIncludeComments(boolean includeComments) {
        this.includeComments = includeComments;
    }

    public JavaFile generateInterfaceLookup(List<CommInterface> interfaces) {
        var classBuilder = TypeSpec.classBuilder("GlcdInterfaceLookup").addModifiers(Modifier.PUBLIC);
        var staticBlockBuilder = CodeBlock.builder();

        ClassName commInfoClass = ClassName.get(GlcdInterfaceInfo.class);
        ClassName arrayList = ClassName.get("java.util", "ArrayList");
        TypeName listOfInfoClass = ParameterizedTypeName.get(arrayList, commInfoClass);

        var fieldBuilder = FieldSpec.builder(listOfInfoClass, "interfaceList")
                                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                                    .initializer("new $T<>()", arrayList);

        //add field
        classBuilder.addField(fieldBuilder.build());

        for (CommInterface commInt : interfaces) {
            staticBlockBuilder.addStatement("interfaceList.add(new $T($L, $L, $S, $S, $S, $S, $S, $S, $S, $S))",
                                            GlcdInterfaceInfo.class,
                                            commInt.index(),
                                            commInt.protocol(),
                                            commInt.name(),
                                            commInt.setPinFunction(),
                                            commInt.arduinoComProcedure(),
                                            commInt.arduinoGpioProcedure(),
                                            commInt.pinsWithType(),
                                            commInt.pinsPlain(),
                                            commInt.pinsMarkdown(),
                                            commInt.genericComProcedure());
        }

        //add static block
        classBuilder.addStaticBlock(staticBlockBuilder.build());

        //add method
        classBuilder.addMethod(MethodSpec.methodBuilder("getInfoList")
                                         .addStatement("return $T.interfaceList", GlcdInterfaceLookup.class)
                                         .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                                         .returns(listOfInfoClass)
                                         .build()
        );

        JavaFile.Builder javaBuilder = JavaFile.builder("com.ibasco.ucgdisplay.drivers.glcd", classBuilder.build());
        if (includeComments)
            javaBuilder.addFileComment(generateFileComment(false));
        return javaBuilder.build();
    }

    public String generateManifest(List<Controller> controllers) {
        Manifest manifest = new Manifest();
        manifest.setControllers(controllers);
        manifest.setLastUpdated(ZonedDateTime.now());
        manifest.setMd5Hash(generateMD5Hash(controllers));
        return gson.toJson(manifest, Manifest.class);
    }

    public String generateMD5Hash(List<Controller> controllers) {
        try {
            // MessageDigest instance for MD5
            MessageDigest md = MessageDigest.getInstance("MD5");

            Type colType = new TypeToken<List<Controller>>() {
            }.getType();

            // Update MessageDigest with input text in bytes
            md.update(gson.toJson(controllers, colType).getBytes());

            // Get the hashbytes
            byte[] hashBytes = md.digest();

            //Convert hash bytes to hex format
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isExcluded(Controller controller, List<String> excludedControllers) {
        return excludedControllers.stream().anyMatch(p -> p.equalsIgnoreCase(controller.getName().trim()));
    }

    public JavaFile generateGlcdCode(List<Controller> controllers, List<String> excludedControllers) {
        TypeSpec.Builder glcdInterfaceBuilder = TypeSpec.interfaceBuilder("Glcd").addModifiers(Modifier.PUBLIC);
        glcdInterfaceBuilder.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "\"unused\"").build());

        for (Controller controller : controllers) {
            if (isExcluded(controller, excludedControllers)) {
                log.warn("generateGlcdCode() : Excluded controller: {}", controller.getName());
                continue;
            }
            TypeSpec.Builder controllerSpecBuilder = TypeSpec.interfaceBuilder(controller.getName()).addModifiers(Modifier.STATIC, Modifier.PUBLIC);
            controllerSpecBuilder.addJavadoc("Display Controller: $L\n", controller.getName());
            for (Vendor vendor : controller.getVendorList()) {
                String vendorName = formatVendorName(vendor);

                FieldSpec.Builder displayFieldBuilder = FieldSpec.builder(GlcdDisplay.class, vendorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
                displayFieldBuilder.addJavadoc("<p>\nDisplay Name:\n    $L :: $L\n</p>\n<p>\nDisplay Width:\n    $L pixels\n</p>\n<p>\nDisplay height:\n    $L pixels\n</p>\nSupported Bus Interfaces: \n<ul>$L</ul>\n<p>\nNotes from author:\n    $L\n</p>\n",
                                               controller.getName(),
                                               vendor.getName(),
                                               vendor.getTileWidth() * 8,
                                               vendor.getTileHeight() * 8,
                                               getSupportedCommProtocols(vendor),
                                               !StringUtils.isBlank(vendor.getNotes()) ? vendor.getNotes() : "N/A"
                );

                String bufferLayout;
                if ("u8g2_ll_hvline_horizontal_right_lsb".equalsIgnoreCase(vendor.getBufferLayout())) {
                    bufferLayout = "HORIZONTAL";
                } else if ("u8g2_ll_hvline_vertical_top_lsb".equalsIgnoreCase(vendor.getBufferLayout())) {
                    bufferLayout = "VERTICAL";
                } else {
                    bufferLayout = "UNKNOWN";
                }

                CodeBlock.Builder displayCodeBlockBuilder = CodeBlock.builder()
                                                                     .add("new $T(", GlcdDisplay.class)
                                                                     .add("\n    $T.$L,", GlcdController.class, controller.getName())
                                                                     .add("\n    ").add("$S,", vendorName)
                                                                     .add("\n    ").add("$L,", vendor.getTileWidth())
                                                                     .add("\n    ").add("$L,", vendor.getTileHeight())
                                                                     .add("\n    ").add("$T.$L,", GlcdBufferLayout.class, bufferLayout);

                CodeBlock.Builder setupCodeBlock = CodeBlock.builder();

                int configSize = vendor.getVendorConfigs().size();
                for (int i = 0; i < configSize; i++) {
                    VendorConfig config = vendor.getVendorConfigs().get(i);
                    String commInts = config.getSupportedInterfaces().stream().map(Comm::getName).distinct().collect(Collectors.joining(" | "));
                    setupCodeBlock.add("\n    new $T($S, $L)$L", GlcdSetupInfo.class, StringUtils.toU8g2SetupName(config), commInts, (configSize > 1 && i < (configSize - 1)) ? "," : "");
                }

                displayCodeBlockBuilder.add(setupCodeBlock.add("\n)").build());
                displayFieldBuilder.initializer(displayCodeBlockBuilder.build());
                controllerSpecBuilder.addField(displayFieldBuilder.build());
            }

            glcdInterfaceBuilder.addType(controllerSpecBuilder.build());
        }

        JavaFile.Builder javaBuilder = JavaFile.builder("com.ibasco.ucgdisplay.drivers.glcd", glcdInterfaceBuilder.build());

        if (includeComments)
            javaBuilder.addFileComment(generateFileComment(false));
        javaBuilder.addStaticImport(ClassName.bestGuess("com.ibasco.ucgdisplay.core.u8g2.U8g2Graphics"), "*");
        return javaBuilder.build();
    }

    public JavaFile generateControllerTypeEnum(List<Controller> controllers) {
        TypeSpec.Builder enumSpec = TypeSpec.enumBuilder("GlcdController").addModifiers(Modifier.PUBLIC);
        for (Controller controller : controllers)
            enumSpec.addEnumConstant(controller.getName());
        JavaFile.Builder javaBuilder = JavaFile.builder("com.ibasco.ucgdisplay.drivers.glcd.enums", enumSpec.build());
        if (includeComments)
            javaBuilder.addFileComment(generateFileComment(false));
        return javaBuilder.build();
    }

    public JavaFile generateGlcdSizeEnum(List<Controller> controllers) {
        TypeSpec.Builder enumSpec = TypeSpec.enumBuilder("GlcdSize").addModifiers(Modifier.PUBLIC);
        enumSpec.addMethod(
                MethodSpec.constructorBuilder()
                          .addParameter(TypeName.INT, "tileWidth")
                          .addParameter(TypeName.INT, "tileHeight")
                          .addStatement("this.tileWidth = tileWidth")
                          .addStatement("this.tileHeight = tileHeight")
                          .build()
        );
        enumSpec.addField(TypeName.INT, "tileWidth", Modifier.PRIVATE);
        enumSpec.addField(TypeName.INT, "tileHeight", Modifier.PRIVATE);
        enumSpec.addMethod(
                MethodSpec.methodBuilder("getDisplayWidth")
                          .addStatement("return tileWidth * 8")
                          .addModifiers(Modifier.PUBLIC)
                          .returns(TypeName.INT)
                          .build()
        );
        enumSpec.addMethod(
                MethodSpec.methodBuilder("getDisplayHeight")
                          .addStatement("return tileHeight * 8", Modifier.PUBLIC)
                          .addModifiers(Modifier.PUBLIC)
                          .returns(TypeName.INT)
                          .build()
        );
        enumSpec.addMethod(
                MethodSpec.methodBuilder("getTileWidth")
                          .addStatement("return tileWidth", Modifier.PUBLIC)
                          .addModifiers(Modifier.PUBLIC)
                          .returns(TypeName.INT)
                          .build()
        );
        enumSpec.addMethod(
                MethodSpec.methodBuilder("getTileHeight")
                          .addStatement("return tileHeight", Modifier.PUBLIC)
                          .addModifiers(Modifier.PUBLIC)
                          .returns(TypeName.INT)
                          .build()
        );
        enumSpec.addMethod(
                MethodSpec.methodBuilder("get")
                          .returns(GlcdSize.class)
                          .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                          .addParameter(TypeName.INT, "tileWidth")
                          .addParameter(TypeName.INT, "tileHeight")
                          .addStatement("return $T.stream(GlcdSize.values())\n" +
                                                "                .filter(p -> (p.getTileWidth() == tileWidth) && (p.getTileHeight() == tileHeight))\n" +
                                                "                .findFirst().orElse(null)", Arrays.class)
                          .build()
        );
        for (Controller controller : controllers) {
            for (Vendor vendor : controller.getVendorList()) {
                int width = vendor.getTileWidth() * 8;
                int height = vendor.getTileHeight() * 8;
                enumSpec.addEnumConstant(String.format("SIZE_%dx%d", width, height), TypeSpec.anonymousClassBuilder("$L, $L", vendor.getTileWidth(), vendor.getTileHeight()).build());
            }
        }
        JavaFile.Builder javaBuilder = JavaFile.builder("com.ibasco.ucgdisplay.drivers.glcd.enums", enumSpec.build());
        if (includeComments)
            javaBuilder.addFileComment(generateFileComment(false));
        return javaBuilder.build();
    }

    public JavaFile generateGlcdFontEnum(String branchName, List<String> exclusions) throws IOException {
        TypeSpec.Builder enumSpec = TypeSpec.enumBuilder("GlcdFont").addModifiers(Modifier.PUBLIC);

        enumSpec.addField(String.class, "fontKey", Modifier.PRIVATE);
        enumSpec.addField(String.class, "fontDescription", Modifier.PRIVATE);
        enumSpec.addField(Integer.class, "glyphCount", Modifier.PRIVATE);
        enumSpec.addField(Integer.class, "glyphTotal", Modifier.PRIVATE);

        enumSpec.addMethod(MethodSpec.constructorBuilder()
                                     .addParameter(TypeName.get(String.class), "fontKey")
                                     .addParameter(TypeName.INT, "glyphCount")
                                     .addParameter(TypeName.INT, "glyphTotal")
                                     .addParameter(TypeName.get(String.class), "fontDescription")
                                     .addStatement("this.fontKey = fontKey")
                                     .addStatement("this.glyphCount = glyphCount")
                                     .addStatement("this.glyphTotal = glyphTotal")
                                     .addStatement("this.fontDescription = fontDescription")
                                     .build());

        enumSpec.addMethod(
                MethodSpec.methodBuilder("getKey")
                          .addModifiers(Modifier.PUBLIC)
                          .returns(String.class)
                          .addStatement("return fontKey")
                          .build()
        );
        enumSpec.addMethod(
                MethodSpec.methodBuilder("getGlyphCount")
                          .addModifiers(Modifier.PUBLIC)
                          .returns(TypeName.INT)
                          .addStatement("return glyphCount")
                          .build()
        );
        enumSpec.addMethod(
                MethodSpec.methodBuilder("getGlyphTotal")
                          .addModifiers(Modifier.PUBLIC)
                          .returns(TypeName.INT)
                          .addStatement("return glyphTotal")
                          .build()
        );
        enumSpec.addMethod(
                MethodSpec.methodBuilder("getDescription")
                          .addModifiers(Modifier.PUBLIC)
                          .returns(String.class)
                          .addStatement("return fontDescription")
                          .build()
        );

        var fonts = extractor.extractFontFilesFromZip(GithubService.REPO_OWNER);

        if (fonts == null)
            throw new IllegalStateException("Unable to fetch fonts from Github service");

        for (var font : fonts) {
            String fontKey = font.name().replaceAll("\\.c", "");
            String name = font.name().replaceAll("u8g2_|\\.c", "").toUpperCase();
            if (exclusions.contains(fontKey)) {
                log.debug("generateGlcdFontEnum() : Excluded font: {}", font);
                continue;
            }
            enumSpec.addEnumConstant(name, TypeSpec.anonymousClassBuilder("$S, $L, $L, $S", fontKey, font.glyphCount(), font.glyphTotal(), font.desc()).build());
        }
        JavaFile.Builder javaBuilder = JavaFile.builder("com.ibasco.ucgdisplay.drivers.glcd.enums", enumSpec.build());
        if (includeComments)
            javaBuilder.addFileComment(generateFileComment(false));
        return javaBuilder.build();
    }

    public String generateFontLookupTableCpp(String branch, List<String> exclusions) {
        CodeBuilder code = new CodeBuilder();
        code.setUseUnixStyleSeparator(true);
        if (includeComments)
            code.appendLine(generateFileComment(true));

        List<String> fonts = fetchFontsFromLatestBranch(branch);

        code.appendLine("#include \"U8g2Hal.h\"");
        code.appendMultiLine("#include <iostream>", 2);

        code.appendLine("void U8g2hal_InitFonts(u8g2_lookup_font_map_t &font_map) {");
        code.appendTabbedLine("font_map.clear();");
        for (String fontName : fonts) {
            if (exclusions.contains(fontName)) {
                log.warn("generateFontLookupTableCpp : Excluded font: {}", fontName);
                code.appendTabbedLine("//Excluded: font_map[\"%s\"] = %s;", fontName, fontName);
                continue;
            }
            code.appendTabbedLine("font_map[\"%s\"] = %s;", fontName, fontName);
        }
        code.append("}");
        return code.toString();
    }

    public String generateSetupLookupTableCpp(List<Controller> controllers, List<String> excludedControllers) {
        CodeBuilder code = new CodeBuilder();
        code.setUseUnixStyleSeparator(true);
        if (includeComments)
            code.appendLine(generateFileComment(true));

        code.appendLine("#include \"U8g2Hal.h\"");
        code.appendMultiLine("#include <iostream>", 2);

        code.appendLine("void U8g2hal_InitSetupFunctions(u8g2_setup_func_map_t &setup_map) {");
        code.appendTabbedLine("setup_map.clear();");
        for (var controller : controllers) {
            if (isExcluded(controller, excludedControllers)) {
                log.warn("generateSetupLookupTableCpp(): Excluded controller: {}", controller.getName());
                continue;
            }
            for (var vendor : controller.getVendorList()) {
                for (VendorConfig config : vendor.getVendorConfigs()) {
                    String name = StringUtils.toU8g2SetupName(config);
                    code.appendTabbedLine("setup_map[\"%s\"] = %s;", name, name);
                }
            }
        }
        code.append("}");
        return code.toString();
    }

    public String generateU8g2CmakeFile(String branch) {
        var code = new CodeBuilder();
        code.setUseUnixStyleSeparator(true);

        if (includeComments) {
            code.appendLine(generateFileComment(true, "#"));
            code.appendLine();
        }

        code.appendLine("include(ExternalProject)");
        code.appendLine();
        code.appendLine("set(PROJ_PREFIX \"u8g2\")");
        code.appendLine();

        code.appendLine("#1: https://github.com/olikraus/u8g2.git");
        code.appendLine("#2: /home/raffy/projects/u8g2-ribasco");
        code.appendLine("#3: https://github.com/ribasco/u8g2.git");
        code.appendLine();

        code.appendLine("ExternalProject_Add(\n" +
                                "        project_u8g2\n" +
                                "        GIT_REPOSITORY \"https://github.com/ribasco/u8g2.git\"\n" +
                                "        GIT_TAG \"master\"\n" +
                                "        PREFIX ${PROJ_PREFIX}\n" +
                                "        INSTALL_COMMAND \"\"\n" +
                                "        CONFIGURE_COMMAND \"\"\n" +
                                "        BUILD_COMMAND \"\"\n" +
                                ")");
        code.appendLine();

        code.appendLine("ExternalProject_Get_Property(project_u8g2 SOURCE_DIR INSTALL_DIR)");
        code.appendLine();

        code.appendLine("# Hack (See: https://stackoverflow.com/questions/45516209/cmake-how-to-use-interface-include-directories-with-externalproject)");
        code.appendLine("file(MAKE_DIRECTORY ${SOURCE_DIR}/csrc)");
        code.appendLine();

        code.appendLine("# Define all the sources");
        code.appendLine("list(APPEND U8G2_SRC");
        List<String> u8g2SourceFiles = fetchU8g2SourceFilesFromBranch(branch);

        for (String sourceFile : u8g2SourceFiles) {
            code.appendTabbedLine("\"${SOURCE_DIR}/csrc/%s\"", sourceFile);
        }
        code.appendTabbedLine(")");
        code.appendLine();

        code.appendLine("add_library(u8g2 STATIC ${U8G2_SRC})");
        code.appendLine("add_dependencies(u8g2 project_u8g2)");
        code.appendLine("target_include_directories(u8g2 PUBLIC \"${SOURCE_DIR}/csrc\")");
        code.appendLine();

        code.appendLine("# Mark these source files as generated or cmake will throw an error during reload");
        code.appendLine("# - Ref 1: https://cmake.org/cmake/help/v3.12/prop_sf/GENERATED.html");
        code.appendLine("# - Ref 2: https://stackoverflow.com/questions/47812230/cmake-make-add-library-depend-on-externalproject-add");
        code.appendLine("set_source_files_properties(${U8G2_SRC} PROPERTIES GENERATED TRUE)");

        return code.toString();
    }

    private void createFieldGetter(TypeSpec.Builder enumBuilder, MethodSpec.Builder constructorBuilder, Type type, String field) {
        //Add field
        enumBuilder.addField(type, field, Modifier.PRIVATE, Modifier.FINAL);
        //Add constructor param
        constructorBuilder.addParameter(TypeName.get(type), field).addStatement(String.format("this.%s = %s", field, field));
        String methodName = "get" + field.substring(0, 1).toUpperCase() + field.substring(1);
        //Add getter method
        enumBuilder.addMethod(
                MethodSpec.methodBuilder(methodName)
                          .addModifiers(Modifier.PUBLIC)
                          .returns(type)
                          .addStatement("return " + field)
                          .build()
        );
    }


    private String getSupportedCommProtocols(Vendor vendor) {
        ArrayList<String> interfaces = new ArrayList<>();
        for (var config : vendor.getVendorConfigs()) {
            for (var busInt : config.getSupportedInterfaces()) {
                String description = switch (busInt.getName().trim()) {
                    case "COM_4WSPI" -> "<li>4-Wire SPI protocol</li>";
                    case "COM_3WSPI" -> "<li>3-Wire SPI protocol</li>";
                    case "COM_6800" -> "<li>Parallel 8-bit 6800 protocol</li>";
                    case "COM_8080" -> "<li>Parallel 8-bit 8080 protocol</li>";
                    case "COM_I2C" -> "<li>I2C protocol</li>";
                    case "COM_UART" -> "<li>Serial/UART protocol</li>";
                    case "COM_KS0108" -> "<li>Parallel 6800 protocol for KS0108 (more chip-select lines)</li>";
                    case "COM_SED1520" -> "<li>Special protocol for SED1520</li>";
                    default -> "<li>UNKNOWN</li>";
                };
                interfaces.add(description);
            }
        }
        return String.join("\n", interfaces).replaceAll("\\t", " ".repeat(4));
    }

    private List<String> fetchU8g2SourceFilesFromBranch(String branchName) {
        try {
            if (StringUtils.isBlank(branchName))
                throw new IllegalArgumentException("Branch name must not be empty");
            if (StringUtils.isBlank(lastU8g2Branch) || !lastU8g2Branch.equals(branchName) || u8g2FileCache.isEmpty()) {
                List<GithubTreeNode> files = githubService.getNodesFromTree("csrc/", branchName);
                u8g2FileCache = files.stream()
                                     .filter(p -> p.getPath().endsWith(".c") || p.getPath().endsWith(".h"))
                                     .map(m -> Paths.get(m.getPath()).getFileName().toString())
                                     .collect(Collectors.toList());
                lastU8g2Branch = branchName;
            }
        } catch (IOException e) {
            log.error("Failed to fetch contents from github", e);
        }
        return u8g2FileCache;
    }

    private List<String> fetchFontsFromLatestBranch(String branchName) {
        try {
            if (StringUtils.isBlank(branchName))
                throw new IllegalArgumentException("Branch name must not be empty");
            if (StringUtils.isBlank(lastFontBranch) || !lastFontBranch.equals(branchName) || fontCache.isEmpty()) {
                List<GithubTreeNode> files = githubService.getNodesFromTree("tools/font/build/single_font_files", branchName);
                fontCache = files.stream()
                                 .filter(p -> p.getPath().endsWith(".c"))
                                 .map(m -> Paths.get(m.getPath()).getFileName().toString())
                                 .filter(name -> name.startsWith("u8g2_"))
                                 .map(n -> n.replace(".c", ""))
                                 .collect(Collectors.toList());
                lastFontBranch = branchName;
            }
        } catch (IOException e) {
            log.error("Failed to fetch contents from github", e);
        }
        return fontCache;
    }

    private String generateFileComment(boolean addCommentBlocks) {
        return generateFileComment(addCommentBlocks, "//");
    }

    private String generateFileComment(boolean addCommentBlocks, String commentKeyword) {
        if (addCommentBlocks)
            return String.format("%s\n%s THIS IS AN AUTO-GENERATED CODE!! DO NOT MODIFY (Last updated: %s)\n%s", commentKeyword, commentKeyword, dateTimeFormatter.format(ZonedDateTime.now()), commentKeyword);
        return String.format("\nTHIS IS AN AUTO-GENERATED CODE!! DO NOT MODIFY (Last updated: %s)\n", dateTimeFormatter.format(ZonedDateTime.now()));
    }
}
