package com.ibasco.ucgdisplay.tools;

import com.ibasco.ucgdisplay.tools.beans.Controller;
import com.squareup.javapoet.JavaFile;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
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
 * - GlcdControllerType.java
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

    private Extractor extractor = new Extractor();

    private Parser parser = new Parser();

    private final Options options = new Options();

    private final CommandLineParser cliParser = new DefaultParser();

    private final HelpFormatter cliFormatter = new HelpFormatter();

    private static final String DEFAULT_BRANCH = "master";

    private Path projectPath;

    private Path fontExclusionFilePath;

    private boolean includeComments;

    private String branchName;

    private boolean testMode = false;

    private URL testResource;

    private InputStream fontExclusions;

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

        String code = (testMode) ? extractor.extractControllersFromUrl(testResource.toExternalForm()) : extractor.extractControllersFromBranch(branchName);
        List<Controller> controllers = parser.parseCode(code);
        CodeGenerator generator = new CodeGenerator();
        generator.setIncludeComments(includeComments);

        List<String> excludedFonts = new ArrayList<>();
        if (fontExclusionFilePath == null) {
            fontExclusions = getClass().getResourceAsStream("/excludedFonts.properties");
        } else {
            fontExclusions = new FileInputStream(fontExclusionFilePath.toFile());
        }

        Scanner scanner = new Scanner(fontExclusions);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            excludedFonts.add(line);
        }
        log.debug("Added {} font exclusions", excludedFonts.size());

        final JavaFile glcdFile = generator.generateGlcdCode(controllers);
        final JavaFile glcdControllerTypes = generator.generateControllerTypeEnum(controllers);
        final JavaFile glcdSize = generator.generateGlcdSizeEnum(controllers);
        final JavaFile glcdFontEnum = generator.generateGlcdFontEnum(branchName, excludedFonts);
        final String fontCppCode = generator.generateFontLookupTableCpp(branchName, excludedFonts);
        final String setupCppCode = generator.generateSetupLookupTableCpp(controllers);

        log.debug(glcdFile.toString());
        log.debug(glcdControllerTypes.toString());
        log.debug(glcdSize.toString());
        log.debug(glcdFontEnum.toString());
        log.debug(fontCppCode);
        log.debug(setupCppCode);

        //Create temp directory
        Path tempDirWithPrefix = Files.createTempDirectory("ucg-code-gen-");

        //Export to Project
        try {
            log.debug("Exporting generated code");
            Path tmpGlcdPath = Paths.get(tempDirWithPrefix.toString(), "Glcd.java");
            Path tmpGlcdControllerTypePath = Paths.get(tempDirWithPrefix.toString(), "GlcdControllerType.java");
            Path tmpGlcdSizePath = Paths.get(tempDirWithPrefix.toString(), "GlcdSize.java");
            Path tmpGlcdFontEnumPath = Paths.get(tempDirWithPrefix.toString(), "GlcdFont.java");
            Path tmpU8g2LookupFontPath = Paths.get(tempDirWithPrefix.toString(), "U8g2LookupFonts.cpp");
            Path tmpU8g2LookupSetupPath = Paths.get(tempDirWithPrefix.toString(), "U8g2LookupSetup.cpp");

            exportCodeToFile(tmpGlcdPath, glcdFile.toString());
            exportCodeToFile(tmpGlcdControllerTypePath, glcdControllerTypes.toString());
            exportCodeToFile(tmpGlcdSizePath, glcdSize.toString());
            exportCodeToFile(tmpGlcdFontEnumPath, glcdFontEnum.toString());
            exportCodeToFile(tmpU8g2LookupFontPath, fontCppCode);
            exportCodeToFile(tmpU8g2LookupSetupPath, setupCppCode);

            if (!Files.isDirectory(projectPath))
                throw new IllegalStateException("Project path is invalid: " + projectPath);

            Path exportPathGlcd = Paths.get(projectPath.toString(), "drivers/glcd/src/main/java/com/ibasco/ucgdisplay/drivers/glcd/Glcd.java");
            Path exportPathGlcdFont = Paths.get(projectPath.toString(), "drivers/glcd/src/main/java/com/ibasco/ucgdisplay/drivers/glcd/enums/GlcdFont.java");
            Path exportPathGlcdSize = Paths.get(projectPath.toString(), "drivers/glcd/src/main/java/com/ibasco/ucgdisplay/drivers/glcd/enums/GlcdSize.java");
            Path exportPathGlcdControllerType = Paths.get(projectPath.toString(), "drivers/glcd/src/main/java/com/ibasco/ucgdisplay/drivers/glcd/enums/GlcdControllerType.java");
            Path exportPathU8g2LookupFontPath = Paths.get(projectPath.toString(), "native/modules/graphics/src/main/cpp/U8g2LookupFonts.cpp");
            Path exportPathU8g2LookupSetupPath = Paths.get(projectPath.toString(), "native/modules/graphics/src/main/cpp/U8g2LookupSetup.cpp");

            //Copy to project path
            exportToProject(tmpGlcdPath, exportPathGlcd);
            exportToProject(tmpGlcdControllerTypePath, exportPathGlcdControllerType);
            exportToProject(tmpGlcdSizePath, exportPathGlcdSize);
            exportToProject(tmpGlcdFontEnumPath, exportPathGlcdFont);
            exportToProject(tmpU8g2LookupFontPath, exportPathU8g2LookupFontPath);
            exportToProject(tmpU8g2LookupSetupPath, exportPathU8g2LookupSetupPath);
        } finally {
            //Cleanup
            recursiveDeleteOnExit(tempDirWithPrefix);
        }
    }

    private void exportToProject(Path source, Path dest) throws IOException {
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        log.info("File \"{}\" exported to project path \"{}\"", source.getFileName(), dest.toString());
    }

    private void exportCodeToFile(Path filePath, String code) throws IOException {
        Files.writeString(filePath, code);
        log.info("File(s) saved to temp directory \"{}\"", filePath.toString());
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
