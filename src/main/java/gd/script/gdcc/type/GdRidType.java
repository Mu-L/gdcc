package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

public final class GdRidType implements GdType {
    public static final GdRidType RID = new GdRidType();

    @Override
    public @NotNull String getTypeName() {
        return "RID";
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.RID;
    }
}
