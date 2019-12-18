package com.ibasco.ucgdisplay.tools;

import static com.ibasco.ucgdisplay.tools.StringUtils.isBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class Extractor {

    private static final Logger log = LoggerFactory.getLogger(Extractor.class);

    private static final String START_TAG = "display_controller_list_start";

    private static final String END_TAG = "display_controller_list_end";

    private static final String DEFAULT_CODEBUILD_URL = "https://raw.githubusercontent.com/olikraus/u8g2/%s/tools/codebuild/codebuild.c";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm:ss a");

    public String extractControllersFromBranch() throws IOException {
        return extractControllersFromBranch("master");
    }

    public String extractControllersFromBranch(String branch) throws IOException {
        String url = String.format(DEFAULT_CODEBUILD_URL, branch);
        log.debug("Extracting controllers from branch: {} (URL: {})", branch, url);
        return extractControllersFromUrl(url);
    }

    public String extractControllersFromUrl(String url) throws IOException {
        log.debug("[DOWNLOAD-EXTRACT] Codebuild URL: {}", url);

        String controllerUrl = (url == null) ? DEFAULT_CODEBUILD_URL : url;

        try (BufferedInputStream in = new BufferedInputStream(new URL(controllerUrl).openStream())) {
            log.info("[DOWNLOAD-EXTRACT] Successfully downloaded codebuild.c from master (Bytes: " + in.available() + ")");
            StringBuilder controllerCode = new StringBuilder();
            boolean startCollecting = false;
            //Start scanning for the controllers array section
            try (Scanner scanner = new Scanner(in)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (isBlank(line))
                        continue;

                    if (line.contains(START_TAG)) {
                        startCollecting = true;
                        continue;
                    } else if (line.contains(END_TAG)) {
                        startCollecting = false;
                        continue;
                    }

                    if (startCollecting) {
                        controllerCode.append(line);
                        controllerCode.append("\n");
                    }
                }
            }
            return controllerCode.toString();
        }
    }

    private String getDefault(int index, String[] args, String defaultArg) {
        if (args.length > 0 && (index <= (args.length - 1))) {
            if (!isBlank(args[index]))
                return args[index];
        }
        return defaultArg;
    }

}
