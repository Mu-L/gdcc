package dev.superice.gdcc.util;

import org.jetbrains.annotations.NotNull;

public final class StringUtil {
    private StringUtil() {
    }

    public static @NotNull String escapeStringLiteral(@NotNull String value) {
        var sb = new StringBuilder();
        for (var i = 0; i < value.length(); ) {
            var codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            switch (codePoint) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (codePoint >= 0x20 && codePoint <= 0x7E) {
                        sb.append((char) codePoint);
                    } else if (codePoint <= 0xFFFF) {
                        sb.append("\\u").append(String.format("%04X", codePoint));
                    } else {
                        sb.append("\\U").append(String.format("%08X", codePoint));
                    }
                }
            }
        }
        return sb.toString();
    }
}
