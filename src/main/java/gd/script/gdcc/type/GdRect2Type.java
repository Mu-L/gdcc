package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static gd.script.gdcc.type.GdFloatVectorType.VECTOR2;

public final class GdRect2Type implements GdCompoundVectorType {
    public static final GdRect2Type RECT2 = new GdRect2Type();

    @Override
    public @NotNull String getTypeName() {
        return "Rect2";
    }

    @Override
    public @NotNull List<GdVectorType> getBaseComponentTypes() {
        return List.of(VECTOR2, VECTOR2);
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.RECT2;
    }
}
