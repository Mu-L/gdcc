package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public final class GdRidType implements GdPrimitiveType {
    public static final GdRidType RID = new GdRidType();

    @Override
    public @NotNull String getTypeName() {
        return "RID";
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.RID;
    }
}
