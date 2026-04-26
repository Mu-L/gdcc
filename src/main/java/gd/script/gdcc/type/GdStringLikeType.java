package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

public sealed interface GdStringLikeType extends GdType
        permits GdNodePathType, GdStringNameType, GdStringType {
    // Marker interface for string-like types (String, StringName, NodePath)

    @Override
    default boolean isNullable() {
        return false;
    }

    @Override
    @NotNull
    GdExtensionTypeEnum getGdExtensionType();

    @Override
    default boolean isDestroyable() {
        return true;
    }
}

