package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

public final class GdBoolType implements GdPrimitiveType {
    public static final GdBoolType BOOL = new GdBoolType();

    @Override
    public @NotNull String getTypeName() {
        return "bool";
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.BOOL;
    }
}
