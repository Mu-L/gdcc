package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

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
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.NIL;
    }

    @Override
    public boolean isDestroyable() {
        return true;
    }
}
