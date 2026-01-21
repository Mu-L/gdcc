package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static dev.superice.gdcc.type.GdFloatVectorType.VECTOR2;

public final class GdTransform2DType implements GdMatrixType {
    public static final GdTransform2DType TRANSFORM2D = new GdTransform2DType();

    @Override
    public @NotNull String getTypeName() {
        return "Transform2D";
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.TRANSFORM2D;
    }

    @Override
    public @NotNull List<GdVectorType> getBaseComponentTypes() {
        return List.of(VECTOR2, VECTOR2, VECTOR2);
    }
}
