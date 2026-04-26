package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;

public final class GdPackedStringArrayType implements GdPackedArrayType {
    public static final GdPackedStringArrayType PACKED_STRING_ARRAY = new GdPackedStringArrayType();

    @Override
    public @NotNull GdType getValueType() {
        return GdStringType.STRING;
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.PACKED_STRING_ARRAY;
    }

    @Override
    public @NotNull String getTypeName() {
        return "PackedStringArray";
    }
}
