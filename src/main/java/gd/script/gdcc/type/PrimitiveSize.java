package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

public enum PrimitiveSize {
    SIZE_1,
    SIZE_8,
    SIZE_16,
    SIZE_32,
    SIZE_64;

    public static @NotNull PrimitiveSize fromInt(int size) {
        return switch (size) {
            case 1 -> SIZE_1;
            case 8 -> SIZE_8;
            case 16 -> SIZE_16;
            case 32 -> SIZE_32;
            case 64 -> SIZE_64;
            default -> throw new IllegalArgumentException("Invalid primitive size: " + size);
        };
    }

    public int toInt() {
        return switch (this) {
            case SIZE_1 -> 1;
            case SIZE_8 -> 8;
            case SIZE_16 -> 16;
            case SIZE_32 -> 32;
            case SIZE_64 -> 64;
        };
    }
}
