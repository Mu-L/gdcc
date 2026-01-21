package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public sealed interface GdPackedArrayType extends GdContainerType permits GdPackedNumericArrayType, GdPackedStringArrayType, GdPackedVectorArrayType {
    @Override
    @NotNull default GdType getKeyType() {
        return GdIntType.INT;
    }

    @Override
    default boolean isNullable() {
        return false;
    }
}
