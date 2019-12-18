package com.ibasco.ucgdisplay.tools.util;

public class CodeBuilder {

    private StringBuilder builder = new StringBuilder();

    private String lineSeparator = System.lineSeparator();

    private boolean useUnixStyleSeparator;

    private static final String TAB = "    ";

    public CodeBuilder append(String code, Object... args) {
        builder.append(String.format(code, args));
        return this;
    }

    public CodeBuilder appendLine(String code, Object... args) {
        appendMultiLine(code, 1, args);
        return this;
    }

    public CodeBuilder appendMultiLine(String code, int lineCount, Object... args) {
        builder.append(code);
        appendLine(lineCount);
        return this;
    }

    public CodeBuilder appendTab(String code) {
        appendTabbedLine(1, code);
        return this;
    }

    public CodeBuilder appendTabbedLine(String code, Object... args) {
        return appendTabbedLine(1, code, args);
    }

    public CodeBuilder appendTabbedLine(int tabCount, String code, Object... args) {
        appendTab(tabCount);
        append(code, args);
        appendLine();
        return this;
    }

    public CodeBuilder appendTab() {
        appendTab(1);
        return this;
    }

    public CodeBuilder appendTab(int count) {
        builder.append(TAB.repeat(Math.max(0, count)));
        return this;
    }

    public CodeBuilder appendLine(int lineCount) {
        if (isUseUnixStyleSeparator())
            builder.append("\n".repeat(Math.max(0, lineCount)));
        else
            builder.append(System.lineSeparator().repeat(Math.max(0, lineCount)));
        return this;
    }

    public CodeBuilder appendLine() {
        return appendLine(1);
    }

    public boolean isUseUnixStyleSeparator() {
        return useUnixStyleSeparator;
    }

    public void setUseUnixStyleSeparator(boolean useUnixStyleSeparator) {
        this.useUnixStyleSeparator = useUnixStyleSeparator;
    }

    @Override
    public String toString() {
        return builder.toString();
    }
}
