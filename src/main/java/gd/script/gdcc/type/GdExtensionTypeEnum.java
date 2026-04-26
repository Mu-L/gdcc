package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum GdExtensionTypeEnum {
    NIL,
    // atomic types
    BOOL,
    INT,
    FLOAT,
    STRING,
    // math types
    VECTOR2,
    VECTOR2I,
    RECT2,
    RECT2I,
    VECTOR3,
    VECTOR3I,
    TRANSFORM2D,
    VECTOR4,
    VECTOR4I,
    PLANE,
    QUATERNION,
    AABB,
    BASIS,
    TRANSFORM3D,
    PROJECTION,
    // misc types
    COLOR,
    STRING_NAME,
    NODE_PATH,
    RID,
    OBJECT,
    CALLABLE,
    SIGNAL,
    DICTIONARY,
    ARRAY,
    // typed arrays
    PACKED_BYTE_ARRAY,
    PACKED_INT32_ARRAY,
    PACKED_INT64_ARRAY,
    PACKED_FLOAT32_ARRAY,
    PACKED_FLOAT64_ARRAY,
    PACKED_STRING_ARRAY,
    PACKED_VECTOR2_ARRAY,
    PACKED_VECTOR3_ARRAY,
    PACKED_COLOR_ARRAY,
    PACKED_VECTOR4_ARRAY;

    public @Nullable GdType getDefaultType() {
        return switch (this) {
            case NIL -> GdNilType.NIL;
            case BOOL -> GdBoolType.BOOL;
            case INT -> GdIntType.INT;
            case FLOAT -> GdFloatType.FLOAT;
            case STRING -> GdStringType.STRING;
            case VECTOR2 -> GdFloatVectorType.VECTOR2;
            case VECTOR2I -> GdIntVectorType.VECTOR2I;
            case RECT2 -> GdRect2Type.RECT2;
            case RECT2I -> GdRect2iType.RECT2I;
            case VECTOR3 -> GdFloatVectorType.VECTOR3;
            case VECTOR3I -> GdIntVectorType.VECTOR3I;
            case TRANSFORM2D -> GdTransform2DType.TRANSFORM2D;
            case VECTOR4 -> GdFloatVectorType.VECTOR4;
            case VECTOR4I -> GdIntVectorType.VECTOR4I;
            case PLANE -> GdPlaneType.PLANE;
            case QUATERNION -> GdQuaternionType.QUATERNION;
            case AABB -> GdAABBType.AABB;
            case BASIS -> GdBasisType.BASIS;
            case TRANSFORM3D -> GdTransform3DType.TRANSFORM3D;
            case COLOR -> GdColorType.COLOR;
            case STRING_NAME -> GdStringNameType.STRING_NAME;
            case NODE_PATH -> GdNodePathType.NODE_PATH;
            case RID -> GdRidType.RID;
            case OBJECT -> GdObjectType.OBJECT;
            case PROJECTION -> GdProjectionType.PROJECTION;
            case PACKED_BYTE_ARRAY -> GdPackedNumericArrayType.PACKED_BYTE_ARRAY;
            case PACKED_INT32_ARRAY -> GdPackedNumericArrayType.PACKED_INT32_ARRAY;
            case PACKED_INT64_ARRAY -> GdPackedNumericArrayType.PACKED_INT64_ARRAY;
            case PACKED_FLOAT32_ARRAY -> GdPackedNumericArrayType.PACKED_FLOAT32_ARRAY;
            case PACKED_FLOAT64_ARRAY -> GdPackedNumericArrayType.PACKED_FLOAT64_ARRAY;
            case PACKED_STRING_ARRAY -> GdPackedStringArrayType.PACKED_STRING_ARRAY;
            case PACKED_VECTOR2_ARRAY -> GdPackedVectorArrayType.PACKED_VECTOR2_ARRAY;
            case PACKED_VECTOR3_ARRAY -> GdPackedVectorArrayType.PACKED_VECTOR3_ARRAY;
            case PACKED_COLOR_ARRAY -> GdPackedVectorArrayType.PACKED_COLOR_ARRAY;
            case PACKED_VECTOR4_ARRAY -> GdPackedVectorArrayType.PACKED_VECTOR4_ARRAY;
            default -> null;
        };
    }

    public @NotNull Class<? extends GdType> getTypeClass() {
        return switch (this) {
            case NIL -> GdNilType.class;
            case BOOL -> GdBoolType.class;
            case INT -> GdIntType.class;
            case FLOAT -> GdFloatType.class;
            case STRING -> GdStringType.class;
            case VECTOR2, VECTOR4, VECTOR3 -> GdFloatVectorType.class;
            case VECTOR2I, VECTOR3I, VECTOR4I -> GdIntVectorType.class;
            case RECT2 -> GdRect2Type.class;
            case RECT2I -> GdRect2iType.class;
            case TRANSFORM2D -> GdTransform2DType.class;
            case PLANE -> GdPlaneType.class;
            case QUATERNION -> GdQuaternionType.class;
            case AABB -> GdAABBType.class;
            case BASIS -> GdBasisType.class;
            case TRANSFORM3D -> GdTransform3DType.class;
            case PROJECTION -> GdProjectionType.class;
            case COLOR -> GdColorType.class;
            case STRING_NAME -> GdStringNameType.class;
            case NODE_PATH -> GdNodePathType.class;
            case RID -> GdRidType.class;
            case OBJECT -> GdObjectType.class;
            case CALLABLE -> GdCallableType.class;
            case SIGNAL -> GdSignalType.class;
            case DICTIONARY -> GdDictionaryType.class;
            case ARRAY -> GdArrayType.class;
            case PACKED_BYTE_ARRAY, PACKED_INT32_ARRAY, PACKED_INT64_ARRAY,
                 PACKED_FLOAT32_ARRAY, PACKED_FLOAT64_ARRAY -> GdPackedNumericArrayType.class;
            case PACKED_STRING_ARRAY -> GdPackedStringArrayType.class;
            case PACKED_VECTOR2_ARRAY, PACKED_VECTOR3_ARRAY,
                 PACKED_COLOR_ARRAY, PACKED_VECTOR4_ARRAY -> GdPackedVectorArrayType.class;
        };
    }
}
