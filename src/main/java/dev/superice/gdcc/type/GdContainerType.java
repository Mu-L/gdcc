package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public sealed interface GdContainerType extends GdType
        permits GdArrayType, GdDictionaryType, GdPackedArrayType {
    @NotNull GdType getKeyType();

    @NotNull GdType getValueType();

    @Override
    @NotNull
    GdExtensionTypeEnum getGdExtensionType();
}
