package com.ibasco.ucgdisplay.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ibasco.ucgdisplay.drivers.glcd.GlcdDisplay;
import com.ibasco.ucgdisplay.drivers.glcd.GlcdSetupInfo;
import com.ibasco.ucgdisplay.drivers.glcd.enums.GlcdBufferType;
import com.ibasco.ucgdisplay.drivers.glcd.enums.GlcdControllerType;
import static com.ibasco.ucgdisplay.tools.StringUtils.formatVendorName;
import com.ibasco.ucgdisplay.tools.beans.*;
import com.ibasco.ucgdisplay.tools.service.GithubService;
import com.ibasco.ucgdisplay.tools.util.CodeBuilder;
import com.squareup.javapoet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerator.class);

    private final GithubService githubService = new GithubService();

    private List<String> fontCache = new ArrayList<>();

    private String lastFontBranch;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private boolean includeComments;

    public boolean isIncludeComments() {
        return includeComments;
    }

    public void setIncludeComments(boolean includeComments) {
        this.includeComments = includeComments;
    }

    public String buildControllerManifest(List<Controller> controllers) {
        Manifest manifest = new Manifest();
        manifest.setControllers(controllers);
        manifest.setLastUpdated(ZonedDateTime.now());
        manifest.setMd5Hash(getMD5ControllersHash(controllers));
        return gson.toJson(manifest, Manifest.class);
    }

    public String getMD5ControllersHash(List<Controller> controllers) {
        try {
            // MessageDigest instance for MD5
            MessageDigest md = MessageDigest.getInstance("MD5");

            Type colType = new TypeToken<List<Controller>>() {}.getType();

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

    public JavaFile generateGlcdCode(List<Controller> controllers) {
        TypeSpec.Builder glcdInterfaceBuilder = TypeSpec.interfaceBuilder("Glcd").addModifiers(Modifier.PUBLIC);

        for (Controller controller : controllers) {
            TypeSpec.Builder controllerSpecBuilder = TypeSpec.interfaceBuilder(controller.getName()).addModifiers(Modifier.STATIC, Modifier.PUBLIC);
            for (Vendor vendor : controller.getVendorList()) {
                String vendorName = formatVendorName(vendor);
                FieldSpec.Builder displayFieldBuilder = FieldSpec.builder(ClassName.bestGuess("GlcdDisplay"), vendorName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
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
                        .add("\n    $T.$L,", GlcdControllerType.class, controller.getName())
                        .add("\n    ").add("$S,", vendorName)
                        .add("\n    ").add("$L,", vendor.getTileWidth())
                        .add("\n    ").add("$L,", vendor.getTileHeight())
                        .add("\n    ").add("$T.$L,", GlcdBufferType.class, bufferLayout);
                CodeBlock.Builder setupCodeBlock = CodeBlock.builder();
                for (VendorConfig config : vendor.getVendorConfigs()) {
                    String commInts = config.getSupportedInterfaces().stream().map(Comm::getName).collect(Collectors.joining(" | "));
                    setupCodeBlock.add("\n    new $T($S, $L)", GlcdSetupInfo.class, StringUtils.toU8g2SetupName(config), commInts);
                }
                displayCodeBlockBuilder.add(setupCodeBlock.add("\n)").build());
                displayFieldBuilder.initializer(displayCodeBlockBuilder.build());
                controllerSpecBuilder.addField(displayFieldBuilder.build());
            }

            glcdInterfaceBuilder.addType(controllerSpecBuilder.build());
        }

        JavaFile.Builder javaBuilder = JavaFile.builder("com.ibasco.ucgdisplay.drivers.glcd", glcdInterfaceBuilder.build());
        /*javaBuilder.addStaticImport(ClassName.bestGuess("com.ibasco.ucgdisplay.drivers.glcd.enums.GlcdBufferType"), "GlcdBufferType");
        javaBuilder.addStaticImport(ClassName.bestGuess("com.ibasco.ucgdisplay.drivers.glcd.enums.GlcdControllerType"), "GlcdControllerType");*/
        javaBuilder.addStaticImport(ClassName.bestGuess("com.ibasco.ucgdisplay.core.u8g2.U8g2Graphics"), "*");
        return javaBuilder.build();
    }

    public JavaFile generateControllerTypeEnum(List<Controller> controllers) {
        TypeSpec.Builder enumSpec = TypeSpec.enumBuilder("GlcdControllerType").addModifiers(Modifier.PUBLIC);
        for (Controller controller : controllers)
            enumSpec.addEnumConstant(controller.getName());
        JavaFile.Builder javaBuilder = JavaFile.builder("com.ibasco.ucgdisplay.drivers.glcd.enums", enumSpec.build());
        return javaBuilder.build();
    }

    public JavaFile generateGlcdSizeEnum(List<Controller> controllers) {
        TypeSpec.Builder enumSpec = TypeSpec.enumBuilder("GlcdSize").addModifiers(Modifier.PUBLIC);
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
                        .addStatement("return tileWidth", Modifier.PUBLIC)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeName.INT)
                        .build()
        );
        enumSpec.addMethod(
                MethodSpec.methodBuilder("get")
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
        return javaBuilder.build();
    }

    public JavaFile generateGlcdFontEnum(String branchName, List<String> exclusions) {
        TypeSpec.Builder enumSpec = TypeSpec.enumBuilder("GlcdFont").addModifiers(Modifier.PUBLIC);

        enumSpec.addField(String.class, "fontKey", Modifier.PRIVATE);
        enumSpec.addMethod(MethodSpec.constructorBuilder()
                                   .addParameter(TypeName.get(String.class), "GlcdFont")
                                   .addStatement("this.fontKey = fontKey")
                                   .build())
        ;
        enumSpec.addMethod(
                MethodSpec.methodBuilder("getKey")
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return fontKey")
                        .build()
        );

        List<String> fonts = fetchFontsFromLatestBranch(branchName);

        if (fonts == null)
            throw new IllegalStateException("Unable to fetch fonts from Github service");

        for (String font : fonts) {
            String fontKey = font.replaceAll(".c", "");
            String name = font.replaceAll("u8g2_|\\.c", "").toUpperCase();
            if (exclusions.contains(font)) {
                log.warn("generateGlcdFontEnum() : Excluded font: {}", font);
                continue;
            }
            enumSpec.addEnumConstant(name, TypeSpec.anonymousClassBuilder("$S", fontKey).build());
        }
        JavaFile.Builder javaBuilder = JavaFile.builder("com.ibasco.ucgdisplay.drivers.glcd.enums", enumSpec.build());
        return javaBuilder.build();
    }

    public String generateFontLookupTableCpp(String branch, List<String> exclusions) {
        CodeBuilder code = new CodeBuilder();
        code.setUseUnixStyleSeparator(true);

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

    public String generateSetupLookupTableCpp(List<Controller> controllers) {
        CodeBuilder code = new CodeBuilder();
        code.setUseUnixStyleSeparator(true);

        code.appendLine("#include \"U8g2Hal.h\"");
        code.appendMultiLine("#include <iostream>", 2);

        code.appendLine("void U8g2hal_InitSetupFunctions(u8g2_setup_func_map_t &setup_map) {");
        code.appendTabbedLine("setup_map.clear();");
        for (Controller controller : controllers) {
            for (Vendor vendor : controller.getVendorList()) {
                for (VendorConfig config : vendor.getVendorConfigs()) {
                    String name = StringUtils.toU8g2SetupName(config);
                    code.appendTabbedLine("setup_map[\"%s\"] = %s;", name, name);
                }
            }
        }
        code.append("}");
        return code.toString();
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
}
