package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public sealed interface GdCompoundVectorType extends GdVectorType
        permits GdAABBType, GdBasisType, GdColorType, GdMatrixType,
        GdPlaneType, GdQuaternionType, GdRect2Type, GdRect2iType {
    @NotNull List<GdVectorType> getBaseComponentTypes();

    @Override
    default @NotNull GdPrimitiveType getElementType() {
        return getBaseComponentTypes().getFirst().getElementType();
    }
}
