package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

public final class GdNilType implements GdType {
    public static final GdNilType NIL = new GdNilType();

    @Override
    public @NotNull String getTypeName() {
        return "Nil";
    }

    @Override
    public boolean isNullable() {
        return true;
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.NIL;
    }
}
