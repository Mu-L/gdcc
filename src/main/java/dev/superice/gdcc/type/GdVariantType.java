package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GdVariantType implements GdType {
    public static final GdVariantType VARIANT = new GdVariantType();

    @Override
    public @NotNull String getTypeName() {
        return "Variant";
    }

    @Override
    public boolean isNullable() {
        return true;
    }

    @Override
    public @Nullable GdExtensionTypeEnum getGdExtensionType() {
        return null;
    }
}
