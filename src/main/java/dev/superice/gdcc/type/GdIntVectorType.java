package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class GdIntVectorType implements GdPureVectorType {
    public static GdIntVectorType VECTOR2I = new GdIntVectorType(2);
    public static GdIntVectorType VECTOR3I = new GdIntVectorType(3);
    public static GdIntVectorType VECTOR4I = new GdIntVectorType(4);

    public final int size;

    public GdIntVectorType(int size) {
        this.size = size;
    }

    @Override
    public @NotNull String getTypeName() {
        return "Vector" + size + "i";
    }

    @Override
    public @NotNull GdPrimitiveType getElementType() {
        return GdIntType.INT;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GdIntVectorType that)) return false;
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
            case 2 -> GdExtensionTypeEnum.VECTOR2I;
            case 3 -> GdExtensionTypeEnum.VECTOR3I;
            case 4 -> GdExtensionTypeEnum.VECTOR4I;
            default -> throw new IllegalStateException("Invalid vector size: " + size);
        };
    }
}
