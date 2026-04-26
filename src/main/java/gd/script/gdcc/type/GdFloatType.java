package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

public final class GdFloatType implements GdNumericType {
    public static final GdFloatType FLOAT = new GdFloatType();

    @Override
    public @NotNull String getTypeName() {
        return "float";
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.FLOAT;
    }
}
