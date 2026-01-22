package dev.superice.gdcc.exception;

import org.jetbrains.annotations.NotNull;

public class LirInsnParsingException extends GdccException {
    public final int lineNumber;
    public final int columnNumber;
    public final @NotNull String lirLine;
    public final @NotNull String reason;

    public LirInsnParsingException(
            int lineNumber,
            int columnNumber,
            @NotNull String lirLine,
            @NotNull String reason
    ) {
        var err = "Error parsing LIR instruction at line " + lineNumber + ", column " + columnNumber + ": " + reason;
        if (!lirLine.isEmpty() && columnNumber < lirLine.length()) {
            err += "\n" + lirLine + "\n" + " ".repeat(columnNumber - 1) + "^";
        }
        super(err);
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.lirLine = lirLine;
        this.reason = reason;
    }
}
