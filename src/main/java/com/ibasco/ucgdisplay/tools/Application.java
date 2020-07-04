package com.ibasco.ucgdisplay.tools;

import com.ibasco.ucgdisplay.tools.service.GithubService;
import com.squareup.javapoet.JavaFile;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Download, extract, parse and generate source code
 * <p>
 * Files to generate:
 * <p>
 * com.ibasco.ucgdisplay.drivers.glcd
 * - Glcd.java
 * <p>
 * com.ibasco.ucgdisplay.drivers.glcd.enums
 * - GlcdController.java
 * - GlcdFont.java
 * - GlcdSize.java
 * <p>
 * native\modules\graphics\src\main\cpp
 * - U8g2LookupFonts.cpp
 * - U8g2LookupSetup.cpp
 *
 * @author Rafael Ibasco
 */
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    private final CodeExtractor extractor = new CodeExtractor();

    private final CodeParser parser = new CodeParser();

    private final Options options = new Options();

    private final CommandLineParser cliParser = new DefaultParser();

    private final HelpFormatter cliFormatter = new HelpFormatter();

    private static final String DEFAULT_BRANCH = "master";

    private Path projectPath;

    private Path fontExclusionFilePath;

    private Path controllerExclusionFilePath;

    private boolean includeComments;

    private String branchName;

    private boolean testMode = false;

    private URL testResource;

    private Application() {
        options.addRequiredOption("p", "path", true, "The base project path where all the files will be automatically exported");
        options.addOption("t", "test", false, "Enable Test Mode");
        options.addOption("b", "branch", true, "Specify a specific branch to extract/process file from");
        options.addOption("c", "inc-comments", false, "When set, comments will be included in the code-generation process");
        options.addOption("h", "help", false, "Print usage");
        options.addOption("f", "exclude-fonts", true, "Specify the lookup file containing the list of fonts to be excluded in the generation process");
    }

    private void initOptions(String[] args) throws ParseException {
        CommandLine cmd = cliParser.parse(options, args);

        if (cmd.hasOption("h")) {
            printUsage();
            System.exit(0);
        }

        if (cmd.hasOption("c")) {
            includeComments = true;
        }

        if (cmd.hasOption("t")) {
            testMode = true;
            testResource = getClass().getResource("/testcodebuild.c");
            log.debug("[OPTION] Test Mode = {}, Test Resource = {}", testMode, testResource);
        }

        if (cmd.hasOption("b")) {
            branchName = cmd.getOptionValue("b", DEFAULT_BRANCH);
            log.debug("[OPTION] Using branch name: {}", branchName);
        } else {
            branchName = DEFAULT_BRANCH;
        }

        if (cmd.hasOption("f")) {
            fontExclusionFilePath = Paths.get(cmd.getOptionValue("f"));
            if (!Files.isRegularFile(fontExclusionFilePath)) {
                throw new ParseException("Invalid font exclusion file path");
            }
        }

        if (cmd.hasOption("p")) {
            projectPath = Paths.get(cmd.getOptionValue("p"));
            if (!Files.isDirectory(projectPath)) {
                throw new ParseException("Not a valid directory path: " + projectPath.toString() + ". Directory not found");
            } else if (!isValidProjectPath()) {
                throw new ParseException("Not a valid ucgdisplay project path: " + projectPath.toString() + ". Make sure the path points to the base project directory");
            }
            log.debug("[OPTION] Using projectPath = {}", projectPath);
        } else {
            throw new ParseException("Missing required option -p (project path)");
        }
    }

    private void run(String[] args) throws Exception {
        try {
            initOptions(args);
        } catch (ParseException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
        }

        String controllerCode = (testMode) ? extractor.extractControllersFromUrl(testResource.toExternalForm()) : extractor.extractControllersFromBranch(branchName);
        String interfaceCode = (testMode) ? extractor.extractInterfacesFromUrl(testResource.toExternalForm()) : extractor.extractInterfacesFromUrl(String.format(CodeExtractor.DEFAULT_CODEBUILD_URL, GithubService.REPO_OWNER, branchName));
        var controllers = parser.parseControllerCode(controllerCode);
        var interfaces = parser.parseInterfaceCode(interfaceCode);
        var generator = new CodeGenerator(extractor);
        generator.setIncludeComments(includeComments);

        //Retrieve exclusions
        var excludedFonts = getExclusions(fontExclusionFilePath, "/excludedFonts.properties");
        var excludedControllers = getExclusions(controllerExclusionFilePath, "/excludedControllers.properties");

        log.info("Added {} font exclusions", excludedFonts.size());
        log.info("Added {} controller exclusions", excludedControllers.size());

        final JavaFile glcdFile = generator.generateGlcdCode(controllers, excludedControllers);
        final JavaFile glcdControllerTypes = generator.generateControllerTypeEnum(controllers);
        final JavaFile glcdSize = generator.generateGlcdSizeEnum(controllers);
        final JavaFile glcdFontEnum = generator.generateGlcdFontEnum(branchName, excludedFonts);
        final JavaFile interfaceLookupCode = generator.generateInterfaceLookup(interfaces);
        final String fontCppCode = generator.generateFontLookupTableCpp(branchName, excludedFonts);
        final String setupCppCode = generator.generateSetupLookupTableCpp(controllers, excludedControllers);
        final String u8g2CmakeFile = generator.generateU8g2CmakeFile(branchName);
        //Create temp directory
        var tempDirWithPrefix = Files.createTempDirectory("ucg-code-gen-");

        //Export to Project
        try {
            log.info("Exporting generated code");
            var tmpGlcdPath = Paths.get(tempDirWithPrefix.toString(), "Glcd.java");
            var tmpGlcdControllerTypePath = Paths.get(tempDirWithPrefix.toString(), "GlcdController.java");
            var tmpGlcdSizePath = Paths.get(tempDirWithPrefix.toString(), "GlcdSize.java");
            var tmpGlcdFontEnumPath = Paths.get(tempDirWithPrefix.toString(), "GlcdFont.java");
            var tmpU8g2LookupFontPath = Paths.get(tempDirWithPrefix.toString(), "U8g2LookupFonts.cpp");
            var tmpU8g2LookupSetupPath = Paths.get(tempDirWithPrefix.toString(), "U8g2LookupSetup.cpp");
            var tmpU8g2CmakeFilePath = Paths.get(tempDirWithPrefix.toString(), "u8g2.cmake");
            var tmpControllerManifest = Paths.get(tempDirWithPrefix.toString(), "controllers.json");
            var tmpGlcdInterfaceLookupPath = Paths.get(tempDirWithPrefix.toString(), "GlcdInterfaceLookup.java");

            exportCodeToFile(tmpGlcdPath, glcdFile.toString());
            exportCodeToFile(tmpGlcdControllerTypePath, glcdControllerTypes.toString());
            exportCodeToFile(tmpGlcdSizePath, glcdSize.toString());
            exportCodeToFile(tmpGlcdFontEnumPath, glcdFontEnum.toString());
            exportCodeToFile(tmpU8g2LookupFontPath, fontCppCode);
            exportCodeToFile(tmpU8g2LookupSetupPath, setupCppCode);
            exportCodeToFile(tmpU8g2CmakeFilePath, u8g2CmakeFile);
            exportCodeToFile(tmpGlcdInterfaceLookupPath, interfaceLookupCode.toString());

            if (!Files.isDirectory(projectPath))
                throw new IllegalStateException("Project path is invalid: " + projectPath);

            var exportPathGlcd = Paths.get(projectPath.toString(), "drivers/glcd/src/main/java/com/ibasco/ucgdisplay/drivers/glcd/Glcd.java");
            var exportPathGlcdFont = Paths.get(projectPath.toString(), "drivers/glcd/src/main/java/com/ibasco/ucgdisplay/drivers/glcd/enums/GlcdFont.java");
            var exportPathGlcdSize = Paths.get(projectPath.toString(), "drivers/glcd/src/main/java/com/ibasco/ucgdisplay/drivers/glcd/enums/GlcdSize.java");
            var exportPathGlcdControllerType = Paths.get(projectPath.toString(), "drivers/glcd/src/main/java/com/ibasco/ucgdisplay/drivers/glcd/enums/GlcdController.java");
            var exportPathU8g2LookupFontPath = Paths.get(projectPath.toString(), "native/modules/graphics/src/main/cpp/U8g2LookupFonts.cpp");
            var exportPathU8g2LookupSetupPath = Paths.get(projectPath.toString(), "native/modules/graphics/src/main/cpp/U8g2LookupSetup.cpp");
            var exportPathU8g2CmakeFilePath = Paths.get(projectPath.toString(), "native/cmake/external/u8g2.cmake");
            var exportPathManifest = Paths.get(projectPath.toString(), "docs/controllers.json");
            var exportInterfaceLookup = Paths.get(projectPath.toString(), "drivers/glcd/src/main/java/com/ibasco/ucgdisplay/drivers/glcd/GlcdInterfaceLookup.java");

            //Copy to project path
            exportToDest(tmpGlcdPath, exportPathGlcd);
            exportToDest(tmpGlcdControllerTypePath, exportPathGlcdControllerType);
            exportToDest(tmpGlcdSizePath, exportPathGlcdSize);
            exportToDest(tmpGlcdFontEnumPath, exportPathGlcdFont);
            exportToDest(tmpU8g2LookupFontPath, exportPathU8g2LookupFontPath);
            exportToDest(tmpU8g2LookupSetupPath, exportPathU8g2LookupSetupPath);
            exportToDest(tmpU8g2CmakeFilePath, exportPathU8g2CmakeFilePath);
            exportToDest(tmpGlcdInterfaceLookupPath, exportInterfaceLookup);

            //Create manifest
            log.info("Creating manifest file at '{}'", tmpControllerManifest);
            String manifestJson = generator.generateManifest(controllers);
            exportCodeToFile(tmpControllerManifest, manifestJson);
            exportToDest(tmpControllerManifest, exportPathManifest);
        } finally {
            log.info("Cleaning up resources");
            //Cleanup
            recursiveDeleteOnExit(tempDirWithPrefix);
        }
    }

    private List<String> getExclusions(Path path, String defaultResource) throws FileNotFoundException {
        ArrayList<String> output = new ArrayList<>();
        InputStream fontExclusions;
        if (path == null) {
            fontExclusions = getClass().getResourceAsStream(defaultResource);
        } else {
            fontExclusions = new FileInputStream(fontExclusionFilePath.toFile());
        }
        processResourceStream(fontExclusions, output);
        return output;
    }

    private void processResourceStream(InputStream is, List<String> output) {
        try (Scanner scanner = new Scanner(is)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                output.add(line);
            }
        }
    }

    private void exportToDest(Path source, Path dest) throws IOException {
        if (testMode) {
            if (!Files.isWritable(dest))
                throw new IllegalStateException(String.format("File '%s' will not be able to replace '%s'. No write permission", source, dest));
            if (Files.exists(dest)) {
                log.info("[TEST] File '{}' will be able to replace '{}'", source, dest);
            } else {
                log.info("[TEST] File '{}' will be copied directly to '{}'", source, dest);
            }
        } else {
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("File \"{}\" exported to project path \"{}\"", source.getFileName(), dest.toString());
        }
    }

    private void exportCodeToFile(Path filePath, String code) throws IOException {
        Files.writeString(filePath, code);
        log.info("File(s) saved to \"{}\"", filePath.toString());
    }

    public static void recursiveDeleteOnExit(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, @SuppressWarnings("unused") BasicFileAttributes attrs) {
                file.toFile().deleteOnExit();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, @SuppressWarnings("unused") BasicFileAttributes attrs) {
                dir.toFile().deleteOnExit();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void main(String[] args) throws Exception {
        new Application().run(args);
    }

    private void printUsage() {
        cliFormatter.printHelp("java -jar codegen.jar -p <project dir path> <opt> <args>", options, true);
    }

    private String getMainClassName() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StackTraceElement main = stack[stack.length - 1];
        return main.getClassName();
    }

    private boolean isValidProjectPath() {
        Path driversDirPath = Paths.get(projectPath.toString(), "drivers");
        Path nativeDirPath = Paths.get(projectPath.toString(), "native", "modules", "graphics", "src", "main", "cpp");
        if (!Files.isDirectory(driversDirPath))
            return false;
        return Files.isDirectory(nativeDirPath);
    }
}
