package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

public final class GdIntType implements GdNumericType {
    public static final GdIntType INT = new GdIntType();

    @Override
    public @NotNull String getTypeName() {
        return "int";
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.INT;
    }
}
