package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

public sealed interface GdPrimitiveType extends GdType
        permits GdBoolType, GdNumericType {
    @Override
    default boolean isNullable() {
        return false;
    }

    @Override
    @NotNull
    GdExtensionTypeEnum getGdExtensionType();
}
