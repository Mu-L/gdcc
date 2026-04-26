package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static gd.script.gdcc.type.GdFloatVectorType.VECTOR4;

public final class GdColorType implements GdCompoundVectorType {
    public static final GdColorType COLOR = new GdColorType();

    @Override
    public @NotNull List<GdVectorType> getBaseComponentTypes() {
        return List.of(VECTOR4);
    }

    @Override
    public @NotNull String getTypeName() {
        return "Color";
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.COLOR;
    }
}
