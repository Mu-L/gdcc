package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public final class GdNodePathType implements GdStringLikeType {
    public static final GdNodePathType NODE_PATH = new GdNodePathType();

    @Override
    public @NotNull String getTypeName() {
        return "NodePath";
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.NODE_PATH;
    }
}

