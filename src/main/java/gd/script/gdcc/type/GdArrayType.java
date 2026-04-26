package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class GdArrayType implements GdContainerType {
    private final @NotNull GdType elementType;

    public GdArrayType(final @NotNull GdType elementType) {
        this.elementType = elementType;
    }

    @Override
    public @NotNull GdType getKeyType() {
        return GdIntType.INT;
    }

    @Override
    public @NotNull GdType getValueType() {
        return elementType;
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.ARRAY;
    }

    @Override
    public @NotNull String getTypeName() {
        if (isGenericArray()) {
            return "Array";
        }
        return "Array[" + elementType.getTypeName() + "]";
    }

    public boolean isGenericArray() {
        return elementType instanceof GdVariantType;
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        var that = (GdArrayType) o;
        return Objects.equals(elementType, that.elementType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(elementType);
    }
}
