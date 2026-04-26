package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static gd.script.gdcc.type.GdFloatVectorType.VECTOR4;

public final class GdPlaneType implements GdCompoundVectorType {
    public static final GdPlaneType PLANE = new GdPlaneType();

    @Override
    public @NotNull List<GdVectorType> getBaseComponentTypes() {
        return List.of(VECTOR4);
    }

    @Override
    public @NotNull String getTypeName() {
        return "Plane";
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.PLANE;
    }
}
