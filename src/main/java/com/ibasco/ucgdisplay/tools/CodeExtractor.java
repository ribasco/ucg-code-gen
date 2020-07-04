package com.ibasco.ucgdisplay.tools;

import static com.ibasco.ucgdisplay.tools.util.StringUtils.isBlank;

import com.ibasco.ucgdisplay.tools.service.GithubService;
import com.ibasco.ucgdisplay.tools.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CodeExtractor {

    private static final Logger log = LoggerFactory.getLogger(CodeExtractor.class);

    private static final String START_TAG = "display_controller_list_start";

    private static final String END_TAG = "display_controller_list_end";

    public static final String DEFAULT_CODEBUILD_URL = "https://raw.githubusercontent.com/%s/u8g2/%s/tools/codebuild/codebuild.c";

    private static final String DEFAULT_MASTER_ZIP = "https://github.com/%s/u8g2/archive/master.zip";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm:ss a");

    private static final String FONT_DIR_PATH = "u8g2-master/tools/font/build/single_font_files/";

    private static final Pattern PATTERN_INTERFACES = Pattern.compile("(?s)struct\\s*interface\\s*interface\\_list\\[\\]\\s*\\=[\\s\\r\\n]*\\{(.+?)\\}\\;");

    public static record FontEntry(String name, String desc, String copyright, int glyphCount, int glyphTotal) {
        @Override
        public String toString() {
            return "FontEntry{" +
                    "name='" + name + '\'' +
                    ", desc='" + desc + '\'' +
                    ", copyright='" + copyright + '\'' +
                    ", glyphCount=" + glyphCount +
                    ", glyphTotal=" + glyphTotal +
                    '}';
        }
    }

    public String extractControllersFromBranch(String branch) throws IOException {
        String url = String.format(DEFAULT_CODEBUILD_URL, GithubService.REPO_OWNER, branch);
        log.info("Extracting controllers from branch: {} (URL: {})", branch, url);
        return extractControllersFromUrl(url);
    }

    public String extractControllersFromUrl(String url) throws IOException {
        log.info("[DOWNLOAD-CONTROLLERS] Codebuild URL: {}", url);

        try (var in = downloadCodebuildFromUrl(url)) {
            log.info("[DOWNLOAD-CONTROLLERS] Successfully downloaded codebuild.c from master (Bytes: " + in.available() + ")");
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

    public String extractInterfacesFromUrl(String url) throws IOException {
        log.info("[DOWNLOAD-INTERFACES] Codebuild URL: {}", url);
        try (var in = downloadCodebuildFromUrl(url)) {
            log.info("[DOWNLOAD-INTERFACES] Successfully downloaded codebuild.c from master (Bytes: " + in.available() + ")");
            StringBuilder interfaceCode = new StringBuilder();
            try (var scanner = new Scanner(in)) {
                var res = scanner.findAll(PATTERN_INTERFACES).findFirst();
                res.ifPresent(matchResult -> interfaceCode.append(matchResult.group(1)));
            }
            return interfaceCode.toString();
        }
    }

    private BufferedInputStream downloadCodebuildFromUrl(String url) throws IOException {
        url = (url == null) ? String.format(DEFAULT_CODEBUILD_URL, GithubService.REPO_OWNER, "master") : url;
        return new BufferedInputStream(new URL(url).openStream());
    }

    public List<FontEntry> extractFontFilesFromZip(String repoOwner) throws IOException {
        List<FontEntry> fontEntries = new ArrayList<>();

        try (var in = downloadProjectArchiveFromRepo(repoOwner)) {
            if (in == null) {
                log.warn("[DOWNLOAD-PROJECT] No archive was downloaded");
                return null;
            }

            boolean startProcessing = false;

            try (ZipInputStream zipIn = new ZipInputStream(in)) {
                log.info("[DOWNLOAD-PROJECT] Extracting font entries from downloaded file");
                ZipEntry zipEntry;
                while ((zipEntry = zipIn.getNextEntry()) != null) {
                    try {
                        var entryName = zipEntry.getName();
                        if (zipEntry.isDirectory()) {
                            if (FONT_DIR_PATH.equalsIgnoreCase(entryName)) {
                                log.info("[DOWNLOAD-PROJECT] Start processing font files in directory: {}", entryName);
                                startProcessing = true;
                                continue;
                            } else {
                                startProcessing = false;
                            }
                        }
                        if (!startProcessing)
                            continue;
                        var filePath = Paths.get(entryName);
                        var fileName = filePath.getFileName().toString();
                        //only process font files starting with u8g2_font_
                        if (fileName.startsWith("u8g2_font_")) {
                            log.debug("[DOWNLOAD-PROJECT] Processing font file: {}", fileName);
                            FontEntry fontData = processFontData(zipIn, fileName);
                            if (fontData != null)
                                fontEntries.add(fontData);
                        }
                    } finally {
                        zipIn.closeEntry();
                    }
                }
            }
        }
        return fontEntries;
    }

    private FontEntry processFontData(ZipInputStream is, String fileName) throws IOException {
        var scanner = new Scanner(is);
        var commentPattern = Pattern.compile("(?s)\\/\\*.*?\\*\\/");
        var res = scanner.findAll(commentPattern).findFirst();
        String contents = null;
        if (res.isPresent())
            contents = res.get().group();
        //Strip comment start/end delimeters
        if (contents != null) {
            contents = contents.replaceAll("\\/\\*", "");
            contents = contents.replaceAll("\\*\\/", "");
            var arr = contents.trim().split("\\n");
            if (arr.length >= 4) {
                String desc = normalizeFontDesc(extractValue(arr[0]));
                String copyright = extractValue(arr[1]);
                String glyphs = extractValue(arr[2]);
                //String buildMode = extractValue(arr[3]);
                var glyphArr = glyphs.split("/");
                int glyphCount = -1;
                int glyphTotal = -1;
                if (glyphArr.length >= 2) {
                    glyphCount = !StringUtils.isBlank(glyphArr[0]) ? Integer.parseInt(glyphArr[0]) : -1;
                    glyphTotal = !StringUtils.isBlank(glyphArr[1]) ? Integer.parseInt(glyphArr[1]) : -1;
                }
                return new FontEntry(fileName, desc, copyright, glyphCount, glyphTotal);
            } else {
                throw new IllegalStateException("Invalid array length for " + fileName + " = " + arr.length);
            }
        }
        return null;
    }

    private String extractValue(String data) {
        if (!data.contains(":"))
            return data;
        return data.split(":")[1].trim();
    }

    private String normalizeFontDesc(String data) {
        return data.replaceAll("-", " ").replaceAll("_", " ").trim();
    }

    public InputStream downloadProjectArchiveFromRepo(String repoOwner) throws IOException {
        String downloadUrl = String.format(DEFAULT_MASTER_ZIP, repoOwner);
        log.info("[DOWNLOAD-PROJECT] Started downloading project archive from '{}'", downloadUrl);
        var in = new BufferedInputStream(new URL(downloadUrl).openStream());
        if (in.available() > 0) {
            log.info("[DOWNLOAD-PROJECT] Successfully downloaded project archive from master (Bytes: " + in.available() + ")");
            return in;
        }
        return null;
    }

    private String getDefault(int index, String[] args, String defaultArg) {
        if (args.length > 0 && (index <= (args.length - 1))) {
            if (!isBlank(args[index]))
                return args[index];
        }
        return defaultArg;
    }
}
