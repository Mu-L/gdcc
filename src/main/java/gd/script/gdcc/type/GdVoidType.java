package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GdVoidType implements GdType {
    public static final GdVoidType VOID = new GdVoidType();

    @Override
    public @NotNull String getTypeName() {
        return "void";
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public @Nullable GdExtensionTypeEnum getGdExtensionType() {
        return null;
    }
}
