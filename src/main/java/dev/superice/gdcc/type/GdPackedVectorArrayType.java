package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public record GdPackedVectorArrayType(@NotNull GdVectorType elementType) implements GdPackedArrayType {
    public static final GdPackedVectorArrayType PACKED_VECTOR2_ARRAY =
            new GdPackedVectorArrayType(GdFloatVectorType.VECTOR2);
    public static final GdPackedVectorArrayType PACKED_VECTOR3_ARRAY =
            new GdPackedVectorArrayType(GdFloatVectorType.VECTOR3);
    public static final GdPackedVectorArrayType PACKED_VECTOR4_ARRAY =
            new GdPackedVectorArrayType(GdFloatVectorType.VECTOR4);
    public static final GdPackedVectorArrayType PACKED_COLOR_ARRAY =
            new GdPackedVectorArrayType(GdColorType.COLOR);

    public GdPackedVectorArrayType {
        if (elementType instanceof GdPureVectorType pureVectorType) {
            if (pureVectorType.getDimension() < 2 || pureVectorType.getDimension() > 4) {
                throw new IllegalArgumentException("PackedVectorArray can only contain Vector2, Vector3, or Vector4 types.");
            }
        } else if (!(elementType instanceof GdColorType)) {
            throw new IllegalArgumentException("PackedVectorArray can only contain Vector2(i), Vector3(i), Vector4(i), or Color types.");
        }
    }

    @Override
    public @NotNull GdType getValueType() {
        return elementType;
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        if (equals(PACKED_VECTOR2_ARRAY)) {
            return GdExtensionTypeEnum.PACKED_VECTOR2_ARRAY;
        } else if (equals(PACKED_VECTOR3_ARRAY)) {
            return GdExtensionTypeEnum.PACKED_VECTOR3_ARRAY;
        } else if (equals(PACKED_VECTOR4_ARRAY)) {
            return GdExtensionTypeEnum.PACKED_VECTOR4_ARRAY;
        } else if (equals(PACKED_COLOR_ARRAY)) {
            return GdExtensionTypeEnum.PACKED_COLOR_ARRAY;
        } else {
            throw new IllegalStateException("Unknown PackedVectorArray type.");
        }
    }

    @Override
    public @NotNull String getTypeName() {
        return "Packed" + elementType.getTypeName() + "Array";
    }
}
