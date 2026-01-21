package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class GdFloatVectorType implements GdPureVectorType {
    public static GdFloatVectorType VECTOR2 = new GdFloatVectorType(2);
    public static GdFloatVectorType VECTOR3 = new GdFloatVectorType(3);
    public static GdFloatVectorType VECTOR4 = new GdFloatVectorType(4);

    public final int size;

    public GdFloatVectorType(int size) {
        this.size = size;
    }

    @Override
    public @NotNull String getTypeName() {
        return "Vector" + size;
    }

    @Override
    public @NotNull GdPrimitiveType getElementType() {
        return GdFloatType.FLOAT;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GdFloatVectorType that)) return false;
        return size == that.size;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(size);
    }

    @Override
    public int getDimension() {
        return size;
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return switch (size) {
            case 2 -> GdExtensionTypeEnum.VECTOR2;
            case 3 -> GdExtensionTypeEnum.VECTOR3;
            case 4 -> GdExtensionTypeEnum.VECTOR4;
            default -> throw new IllegalStateException("Unknown GdFloatVectorType size: " + size);
        };
    }
}
