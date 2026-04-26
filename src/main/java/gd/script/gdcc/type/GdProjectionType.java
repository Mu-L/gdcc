package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static gd.script.gdcc.type.GdFloatVectorType.VECTOR4;

public final class GdProjectionType implements GdMatrixType {
    public static final GdProjectionType PROJECTION = new GdProjectionType();

    @Override
    public @NotNull String getTypeName() {
        return "Projection";
    }

    @Override
    public @NotNull List<GdVectorType> getBaseComponentTypes() {
        return List.of(VECTOR4, VECTOR4, VECTOR4, VECTOR4);
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.PROJECTION;
    }
}
