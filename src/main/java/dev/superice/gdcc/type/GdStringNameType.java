package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public final class GdStringNameType implements GdStringLikeType {
    public static final GdStringNameType STRING_NAME = new GdStringNameType();

    @Override
    public @NotNull String getTypeName() {
        return "StringName";
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.STRING_NAME;
    }
}

