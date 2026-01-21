package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class GdDictionaryType implements GdContainerType {
    private final @NotNull GdType keyType;
    private final @NotNull GdType valueType;

    public GdDictionaryType(@NotNull GdType keyType, @NotNull GdType valueType) {
        this.keyType = keyType;
        this.valueType = valueType;
    }


    @Override
    public @NotNull GdType getKeyType() {
        return this.keyType;
    }

    @Override
    public @NotNull GdType getValueType() {
        return this.valueType;
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.DICTIONARY;
    }

    @Override
    public @NotNull String getTypeName() {
        if (keyType instanceof GdVariantType && valueType instanceof GdVariantType) {
            return "Dictionary";
        } else {
            return "Dictionary[" + keyType.getTypeName() + ", " + valueType.getTypeName() + "]";
        }
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        var that = (GdDictionaryType) o;
        return Objects.equals(keyType, that.keyType) && Objects.equals(valueType, that.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyType, valueType);
    }
}
