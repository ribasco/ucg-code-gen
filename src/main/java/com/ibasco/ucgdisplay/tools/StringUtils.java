package com.ibasco.ucgdisplay.tools;

import com.ibasco.ucgdisplay.tools.beans.Vendor;
import com.ibasco.ucgdisplay.tools.beans.VendorConfig;

public class StringUtils {

    public static String sanitizeData(String data) {
        //Remove single line comments
        data = data.replaceAll("(?<=\\s)//([^\\n\\r]*)", "");

        //Remove multi-line style comments
        data = data.replaceAll("/\\*(?>[^*/]+|\\*[^/]|/[^*]|/\\*(?>[^*/]+|\\*[^/]|/[^*])*\\*/)*\\*/", "");

        //Remove spaces and line-breaks
        return data.replaceAll("[\\n\\r\\s]", "");
    }

    public static boolean isBlank(final CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0)
            return true;
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i)))
                return false;
        }
        return true;
    }

    public static String toU8g2SetupName(VendorConfig config) {
        return toU8g2SetupName(config, null);
    }

    public static String toU8g2SetupName(VendorConfig config, String bufferCode) {
        StringBuilder setupName = new StringBuilder();
        setupName.append("u8g2_Setup_");
        setupName.append(config.getVendor().getController().getName().toLowerCase());
        setupName.append("_");
        if (!isBlank(config.getCadNameShort())) {
            setupName.append(config.getCadNameShort());
            setupName.append("_");
        }
        setupName.append(config.getVendor().getName().toLowerCase());
        setupName.append("_");
        if (isBlank(bufferCode))
            setupName.append("f"); //buffer code
        else
            setupName.append(bufferCode);
        return setupName.toString();
    }

    public static String formatVendorName(Vendor vendor) {
        return String.format("D_%dx%d_%s", vendor.getTileWidth() * 8, vendor.getTileHeight() * 8, vendor.getName().replaceAll("_", ""));
    }
}
