package dev.superice.gdcc.lir.parser;

import dev.superice.gdcc.exception.TypeParsingException;
import dev.superice.gdcc.type.*;
import org.jetbrains.annotations.NotNull;

/// Utility to parse textual type representations into GdType instances.
public final class TypeParser {
    private TypeParser() {}

    public static @NotNull GdType parse(@NotNull String typeName) {
        var t = typeName.trim();
        if (t.isEmpty()) {
            throw new TypeParsingException("Type name is empty");
        }

        switch (t) {
            case "AABB": return GdAABBType.AABB;
            case "Array": return new GdArrayType(GdVariantType.VARIANT);
            case "Basis": return GdBasisType.BASIS;
            case "bool": return GdBoolType.BOOL;
            case "Callable": return new GdCallableType();
            case "Color": return GdColorType.COLOR;
            case "Dictionary": return new GdDictionaryType(GdVariantType.VARIANT, GdVariantType.VARIANT);
            case "float": return GdFloatType.FLOAT;
            case "Vector2": return GdFloatVectorType.VECTOR2;
            case "Vector2i": return GdIntVectorType.VECTOR2I;
            case "Vector3": return GdFloatVectorType.VECTOR3;
            case "Vector3i": return GdIntVectorType.VECTOR3I;
            case "Vector4": return GdFloatVectorType.VECTOR4;
            case "Vector4i": return GdIntVectorType.VECTOR4I;
            case "int": return GdIntType.INT;
            case "Nil", "null": return GdNilType.NIL;
            case "NodePath": return GdNodePathType.NODE_PATH;
            case "Object": return GdObjectType.OBJECT;
            case "PackedByteArray": return GdPackedNumericArrayType.PACKED_BYTE_ARRAY;
            case "PackedInt32Array": return GdPackedNumericArrayType.PACKED_INT32_ARRAY;
            case "PackedInt64Array": return GdPackedNumericArrayType.PACKED_INT64_ARRAY;
            case "PackedFloat32Array": return GdPackedNumericArrayType.PACKED_FLOAT32_ARRAY;
            case "PackedFloat64Array": return GdPackedNumericArrayType.PACKED_FLOAT64_ARRAY;
            case "PackedStringArray": return GdPackedStringArrayType.PACKED_STRING_ARRAY;
            case "PackedVector2Array": return GdPackedVectorArrayType.PACKED_VECTOR2_ARRAY;
            case "PackedVector3Array": return GdPackedVectorArrayType.PACKED_VECTOR3_ARRAY;
            case "PackedVector4Array": return GdPackedVectorArrayType.PACKED_VECTOR4_ARRAY;
            case "PackedColorArray": return GdPackedVectorArrayType.PACKED_COLOR_ARRAY;
            case "Plane": return GdPlaneType.PLANE;
            case "Projection": return GdProjectionType.PROJECTION;
            case "Quaternion": return GdQuaternionType.QUATERNION;
            case "Rect2": return GdRect2Type.RECT2;
            case "Rect2i": return GdRect2iType.RECT2I;
            case "RID": return GdRidType.RID;
            case "Signal": return new GdSignalType();
            case "String": return GdStringType.STRING;
            case "StringName": return GdStringNameType.STRING_NAME;
            case "Transform2D": return GdTransform2DType.TRANSFORM2D;
            case "Transform3D": return GdTransform3DType.TRANSFORM3D;
            case "void", "Void": return GdVoidType.VOID;
            case "Variant": return GdVariantType.VARIANT;
        }

        // Generic forms: Array[T], Dictionary[K, V]
        if (t.startsWith("Array[") && t.endsWith("]")) {
            var inner = t.substring(6, t.length() - 1).trim();
            if (inner.isEmpty()) {
                throw new TypeParsingException("Array type must specify an element type: " + t);
            }
            return new GdArrayType(parse(inner));
        }
        if (t.startsWith("Dictionary[") && t.endsWith("]")) {
            var inner = t.substring(11, t.length() - 1);
            var parts = inner.split(",");
            if (parts.length == 2) {
                return new GdDictionaryType(parse(parts[0].trim()), parse(parts[1].trim()));
            } else {
                throw new TypeParsingException("Invalid Dictionary type format: " + t);
            }
        }

        // fallback: treat as object/class name
        return new GdObjectType(t);
    }
}
