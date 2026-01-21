package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public final class GdStringType implements GdStringLikeType {
    public static final GdStringType STRING = new GdStringType();

    @Override
    public @NotNull String getTypeName() {
        return "String";
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.STRING;
    }
}

