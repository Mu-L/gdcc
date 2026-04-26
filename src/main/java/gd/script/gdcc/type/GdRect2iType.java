package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static gd.script.gdcc.type.GdIntVectorType.VECTOR2I;

public final class GdRect2iType implements GdCompoundVectorType {
    public static final GdRect2iType RECT2I = new GdRect2iType();

    @Override
    public @NotNull String getTypeName() {
        return "Rect2i";
    }

    @Override
    public @NotNull List<GdVectorType> getBaseComponentTypes() {
        return List.of(VECTOR2I, VECTOR2I);
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.RECT2I;
    }
}
