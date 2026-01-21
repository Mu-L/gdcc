package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public record GdPackedNumericArrayType(PrimitiveSize elementSize,
                                       GdNumericType elementType) implements GdPackedArrayType {
    public static final GdPackedNumericArrayType PACKED_BYTE_ARRAY =
            new GdPackedNumericArrayType(PrimitiveSize.SIZE_8, GdIntType.INT);
    public static final GdPackedNumericArrayType PACKED_INT32_ARRAY =
            new GdPackedNumericArrayType(PrimitiveSize.SIZE_32, GdIntType.INT);
    public static final GdPackedNumericArrayType PACKED_INT64_ARRAY =
            new GdPackedNumericArrayType(PrimitiveSize.SIZE_64, GdIntType.INT);
    public static final GdPackedNumericArrayType PACKED_FLOAT32_ARRAY =
            new GdPackedNumericArrayType(PrimitiveSize.SIZE_32, GdFloatType.FLOAT);
    public static final GdPackedNumericArrayType PACKED_FLOAT64_ARRAY =
            new GdPackedNumericArrayType(PrimitiveSize.SIZE_64, GdFloatType.FLOAT);

    public GdPackedNumericArrayType(@NotNull PrimitiveSize elementSize, @NotNull GdNumericType elementType) {
        this.elementSize = elementSize;
        this.elementType = elementType;
        if (!(elementType instanceof GdIntType || elementType instanceof GdFloatType)) {
            throw new IllegalArgumentException("elementType must be either GdIntType or GdFloatType");
        }
    }

    @Override
    public @NotNull GdType getValueType() {
        return elementType;
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        if (elementType instanceof GdIntType) {
            if (elementSize == PrimitiveSize.SIZE_8) {
                return GdExtensionTypeEnum.PACKED_BYTE_ARRAY;
            } else if (elementSize == PrimitiveSize.SIZE_32) {
                return GdExtensionTypeEnum.PACKED_INT32_ARRAY;
            } else {
                return GdExtensionTypeEnum.PACKED_INT64_ARRAY;
            }
        } else {
            if (elementSize == PrimitiveSize.SIZE_32) {
                return GdExtensionTypeEnum.PACKED_FLOAT32_ARRAY;
            } else {
                return GdExtensionTypeEnum.PACKED_FLOAT64_ARRAY;
            }
        }
    }

    @Override
    public @NotNull String getTypeName() {
        if (elementType instanceof GdIntType) {
            if (elementSize == PrimitiveSize.SIZE_8) {
                return "PackedByteArray";
            }
            return "PackedInt" + elementSize.toInt() + "Array";
        } else {
            return "PackedFloat" + elementSize.toInt() + "Array";
        }
    }
}
