package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public sealed interface GdMetaType extends GdType
        permits GdCallableType, GdSignalType {
    @Override
    @NotNull
    GdExtensionTypeEnum getGdExtensionType();

    @Override
    default boolean isDestroyable() {
        return true;
    }
}
