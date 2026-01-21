package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public sealed interface GdPrimitiveType extends GdType
        permits GdBoolType, GdNumericType, GdRidType, GdVectorType {
    @Override
    default boolean isNullable() {
        return false;
    }

    @Override
    @NotNull
    GdExtensionTypeEnum getGdExtensionType();
}
