package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public sealed interface GdVectorType extends GdType permits GdCompoundVectorType, GdPureVectorType {
    @NotNull GdPrimitiveType getElementType();

    @Override
    default boolean isNullable() {
        return false;
    }
}
