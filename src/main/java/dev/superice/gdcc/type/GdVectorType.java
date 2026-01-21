package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public sealed interface GdVectorType extends GdPrimitiveType permits GdCompoundVectorType, GdPureVectorType {
    @NotNull GdPrimitiveType getElementType();
}
