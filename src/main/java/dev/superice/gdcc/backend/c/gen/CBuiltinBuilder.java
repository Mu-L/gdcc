package dev.superice.gdcc.backend.c.gen;

import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdcc.type.GdArrayType;
import dev.superice.gdcc.type.GdBasisType;
import dev.superice.gdcc.type.GdDictionaryType;
import dev.superice.gdcc.type.GdFloatType;
import dev.superice.gdcc.type.GdIntType;
import dev.superice.gdcc.type.GdObjectType;
import dev.superice.gdcc.type.GdProjectionType;
import dev.superice.gdcc.type.GdTransform2DType;
import dev.superice.gdcc.type.GdTransform3DType;
import dev.superice.gdcc.type.GdType;
import dev.superice.gdcc.type.GdVariantType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Helper for built-in type constructor symbol generation and constructor metadata lookup.
///
/// This helper is intentionally scoped to built-in constructor naming and metadata validation.
public final class CBuiltinBuilder {
    private final @NotNull CGenHelper helper;

    public CBuiltinBuilder(@NotNull CGenHelper helper) {
        this.helper = Objects.requireNonNull(helper);
    }

    /// Render `godot_new_<BuiltinType>` constructor base symbol.
    public @NotNull String renderConstructorBaseName(@NotNull GdType type) {
        return "godot_new_" + helper.renderGdTypeName(type);
    }

    /// Render constructor symbol `godot_new_<BuiltinType>[_with_<argType>...]`.
    ///
    /// `argTypeSuffixes` should match gdextension-lite constructor suffix tokens, e.g.
    /// `float`, `int`, `Vector2`, `utf8_chars`.
    public @NotNull String renderConstructorFunctionName(@NotNull GdType type,
                                                         @NotNull List<String> argTypeSuffixes) {
        var ctorName = renderConstructorBaseName(type);
        if (argTypeSuffixes.isEmpty()) {
            return ctorName;
        }
        var normalizedSuffixes = new ArrayList<String>(argTypeSuffixes.size());
        for (var suffix : argTypeSuffixes) {
            if (suffix == null || suffix.isBlank()) {
                throw new IllegalArgumentException("Constructor argument suffix must not be blank");
            }
            normalizedSuffixes.add(suffix);
        }
        return ctorName + "_with_" + String.join("_", normalizedSuffixes);
    }

    /// Render constructor symbol using GD type names as suffixes.
    public @NotNull String renderConstructorFunctionNameByTypes(@NotNull GdType type,
                                                                @NotNull List<GdType> argTypes) {
        var suffixes = new ArrayList<String>(argTypes.size());
        for (var argType : argTypes) {
            suffixes.add(helper.renderGdTypeName(argType));
        }
        return renderConstructorFunctionName(type, suffixes);
    }

    /// Checks whether ExtensionBuiltinClass metadata contains a constructor with the exact argument type list.
    ///
    /// Matching uses normalized GD type names (`CGenHelper.renderGdTypeName`) to avoid
    /// instance-based equality pitfalls.
    public boolean hasConstructor(@NotNull GdType type, @NotNull List<GdType> argTypes) {
        var builtinClass = helper.context().classRegistry().findBuiltinClass(helper.renderGdTypeName(type));
        if (builtinClass == null) {
            return false;
        }
        var expectedTypeNames = new ArrayList<String>(argTypes.size());
        for (var argType : argTypes) {
            expectedTypeNames.add(helper.renderGdTypeName(argType));
        }
        for (var ctor : builtinClass.constructors()) {
            if (ctor.arguments().size() != expectedTypeNames.size()) {
                continue;
            }
            var matches = true;
            for (var i = 0; i < ctor.arguments().size(); i++) {
                var parsedType = ClassRegistry.tryParseTextType(ctor.arguments().get(i).type());
                if (parsedType == null || !helper.renderGdTypeName(parsedType).equals(expectedTypeNames.get(i))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    /// Construct a builtin value into `target`.
    ///
    /// This API is shared for construct_builtin and container constructions:
    /// - Array / Dictionary follow dedicated typed-constructor path.
    /// - Other builtin types resolve constructor metadata by exact argument type match.
    /// - If API metadata has no exact constructor, fallback to gdcc helper shims:
    ///   Transform2D, Transform3D, Basis, Projection.
    public void constructBuiltin(@NotNull CBodyBuilder bodyBuilder,
                                 @NotNull CBodyBuilder.TargetRef target,
                                 @NotNull List<CBodyBuilder.ValueRef> args) {
        Objects.requireNonNull(bodyBuilder);
        Objects.requireNonNull(target);
        Objects.requireNonNull(args);
        var targetType = target.type();
        switch (targetType) {
            case GdArrayType arrayType -> constructArray(bodyBuilder, target, arrayType, args);
            case GdDictionaryType dictionaryType -> constructDictionary(bodyBuilder, target, dictionaryType, args);
            default -> constructRegularBuiltin(bodyBuilder, target, targetType, args);
        }
    }

    /// Validates constructor availability against ExtensionBuiltinClass metadata.
    /// Helper shim constructors may skip this check.
    public void validateConstructor(@NotNull GdType type,
                                    @NotNull List<GdType> argTypes,
                                    boolean skipApiValidation) {
        if (skipApiValidation) {
            return;
        }
        if (!hasConstructor(type, argTypes)) {
            var argTypeNames = new ArrayList<String>(argTypes.size());
            for (var argType : argTypes) {
                argTypeNames.add(helper.renderGdTypeName(argType));
            }
            throw new IllegalArgumentException("Builtin constructor validation failed: '" +
                    helper.renderGdTypeName(type) + "' with args [" +
                    String.join(", ", argTypeNames) + "] is not defined in ExtensionBuiltinClass");
        }
    }

    private @Nullable List<GdType> resolveHelperShimCtorArgTypes(@NotNull GdType type, int argCount) {
        return switch (type) {
            case GdTransform2DType _ when argCount == 6 -> repeatedCtorArgTypes(GdFloatType.FLOAT, 6);
            case GdTransform3DType _ when argCount == 12 -> repeatedCtorArgTypes(GdFloatType.FLOAT, 12);
            case GdBasisType _ when argCount == 9 -> repeatedCtorArgTypes(GdFloatType.FLOAT, 9);
            case GdProjectionType _ when argCount == 16 -> repeatedCtorArgTypes(GdFloatType.FLOAT, 16);
            default -> null;
        };
    }

    private @NotNull List<GdType> repeatedCtorArgTypes(@NotNull GdType argType, int count) {
        var suffixes = new ArrayList<GdType>(count);
        for (var i = 0; i < count; i++) {
            suffixes.add(argType);
        }
        return suffixes;
    }

    private void constructRegularBuiltin(@NotNull CBodyBuilder bodyBuilder,
                                         @NotNull CBodyBuilder.TargetRef target,
                                         @NotNull GdType targetType,
                                         @NotNull List<CBodyBuilder.ValueRef> args) {
        var ctorArgTypes = new ArrayList<GdType>(args.size());
        for (var arg : args) {
            ctorArgTypes.add(arg.type());
        }
        if (hasConstructor(targetType, ctorArgTypes)) {
            var ctorFunc = renderConstructorFunctionNameByTypes(targetType, ctorArgTypes);
            bodyBuilder.callAssign(target, ctorFunc, targetType, args);
            return;
        }
        var helperCtorArgTypes = resolveHelperShimCtorArgTypes(targetType, ctorArgTypes.size());
        if (helperCtorArgTypes != null && checkExactTypeNames(helperCtorArgTypes, ctorArgTypes)) {
            var ctorFunc = renderConstructorFunctionNameByTypes(targetType, helperCtorArgTypes);
            bodyBuilder.callAssign(target, ctorFunc, targetType, args);
            return;
        }
        var argTypeNames = new ArrayList<String>(ctorArgTypes.size());
        for (var argType : ctorArgTypes) {
            argTypeNames.add(helper.renderGdTypeName(argType));
        }
        throw new IllegalArgumentException("Builtin constructor validation failed: '" +
                helper.renderGdTypeName(targetType) + "' with args [" +
                String.join(", ", argTypeNames) + "] is not defined in ExtensionBuiltinClass");
    }

    private void constructArray(@NotNull CBodyBuilder bodyBuilder,
                                @NotNull CBodyBuilder.TargetRef target,
                                @NotNull GdArrayType arrayType,
                                @NotNull List<CBodyBuilder.ValueRef> args) {
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("Array construction expects no runtime arguments");
        }
        var elementType = arrayType.getValueType();
        if (elementType instanceof GdVariantType) {
            bodyBuilder.callAssign(target, renderConstructorBaseName(arrayType), arrayType, List.of());
            return;
        }

        var baseType = new GdArrayType(GdVariantType.VARIANT);
        var baseTemp = bodyBuilder.newTempVariable("array_base", baseType, "godot_new_Array()");
        bodyBuilder.declareTempVar(baseTemp);

        var ctorArgs = List.of(
                bodyBuilder.valueOfExpr(baseTemp.name(), baseType),
                bodyBuilder.valueOfExpr(renderGdExtensionVariantTypeIntLiteral(elementType), GdIntType.INT),
                bodyBuilder.valueOfStringNamePtrLiteral(resolveTypedContainerClassName(elementType)),
                bodyBuilder.valueOfExpr("NULL", GdObjectType.OBJECT, CBodyBuilder.PtrKind.GODOT_PTR)
        );
        bodyBuilder.callAssign(
                target,
                "godot_new_Array_with_Array_int_StringName_Variant",
                arrayType,
                ctorArgs
        );
        bodyBuilder.destroyTempVar(baseTemp);
    }

    private void constructDictionary(@NotNull CBodyBuilder bodyBuilder,
                                     @NotNull CBodyBuilder.TargetRef target,
                                     @NotNull GdDictionaryType dictionaryType,
                                     @NotNull List<CBodyBuilder.ValueRef> args) {
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("Dictionary construction expects no runtime arguments");
        }
        var keyType = dictionaryType.getKeyType();
        var valueType = dictionaryType.getValueType();
        if (keyType instanceof GdVariantType || valueType instanceof GdVariantType) {
            bodyBuilder.callAssign(target, renderConstructorBaseName(dictionaryType), dictionaryType, List.of());
            return;
        }

        var baseType = new GdDictionaryType(GdVariantType.VARIANT, GdVariantType.VARIANT);
        var baseTemp = bodyBuilder.newTempVariable("dict_base", baseType, "godot_new_Dictionary()");
        bodyBuilder.declareTempVar(baseTemp);

        var ctorArgs = List.of(
                bodyBuilder.valueOfExpr(baseTemp.name(), baseType),
                bodyBuilder.valueOfExpr(renderGdExtensionVariantTypeIntLiteral(keyType), GdIntType.INT),
                bodyBuilder.valueOfStringNamePtrLiteral(resolveTypedContainerClassName(keyType)),
                bodyBuilder.valueOfExpr("NULL", GdObjectType.OBJECT, CBodyBuilder.PtrKind.GODOT_PTR),
                bodyBuilder.valueOfExpr(renderGdExtensionVariantTypeIntLiteral(valueType), GdIntType.INT),
                bodyBuilder.valueOfStringNamePtrLiteral(resolveTypedContainerClassName(valueType)),
                bodyBuilder.valueOfExpr("NULL", GdObjectType.OBJECT, CBodyBuilder.PtrKind.GODOT_PTR)
        );
        bodyBuilder.callAssign(
                target,
                "godot_new_Dictionary_with_Dictionary_int_StringName_Variant_int_StringName_Variant",
                dictionaryType,
                ctorArgs
        );
        bodyBuilder.destroyTempVar(baseTemp);
    }

    private boolean checkExactTypeNames(@NotNull List<GdType> expected,
                                        @NotNull List<GdType> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (var i = 0; i < expected.size(); i++) {
            if (!helper.renderGdTypeName(expected.get(i)).equals(helper.renderGdTypeName(actual.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private @NotNull String resolveTypedContainerClassName(@NotNull GdType type) {
        if (type instanceof GdObjectType objectType) {
            return objectType.getTypeName();
        }
        return "";
    }

    private @NotNull String renderGdExtensionVariantTypeIntLiteral(@NotNull GdType type) {
        var gdType = type.getGdExtensionType();
        if (gdType == null) {
            throw new IllegalArgumentException("Type '" + type.getTypeName() +
                    "' has no GDExtension variant type");
        }
        return "(godot_int)GDEXTENSION_VARIANT_TYPE_" + gdType.name();
    }
}
