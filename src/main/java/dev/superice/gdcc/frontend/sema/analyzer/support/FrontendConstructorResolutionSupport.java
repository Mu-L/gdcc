package dev.superice.gdcc.frontend.sema.analyzer.support;

import dev.superice.gdcc.frontend.sema.FrontendCallResolutionStatus;
import dev.superice.gdcc.gdextension.ExtensionBuiltinClass;
import dev.superice.gdcc.gdextension.ExtensionGdClass;
import dev.superice.gdcc.scope.ClassDef;
import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdcc.scope.FunctionDef;
import dev.superice.gdcc.scope.ParameterDef;
import dev.superice.gdcc.scope.ScopeOwnerKind;
import dev.superice.gdcc.scope.ScopeTypeMeta;
import dev.superice.gdcc.type.GdObjectType;
import dev.superice.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/// Shared constructor-lookup helper for frontend semantic publication.
///
/// The same constructor compatibility rules must back both:
/// - chain route reduction such as `Worker.new(...)`
/// - bare builtin direct constructor calls such as `Vector3i(...)`
///
/// Keeping that logic in one place prevents the frontend from publishing two slightly different
/// constructor surfaces that later CFG/lowering would have to special-case.
final class FrontendConstructorResolutionSupport {
    private FrontendConstructorResolutionSupport() {
    }

    record Resolution(
            @NotNull FrontendCallResolutionStatus status,
            @Nullable Object declarationSite,
            @Nullable ScopeOwnerKind ownerKind,
            @Nullable String detailReason
    ) {
        Resolution {
            Objects.requireNonNull(status, "status must not be null");
            if (status == FrontendCallResolutionStatus.RESOLVED) {
                if (detailReason != null) {
                    throw new IllegalArgumentException("detailReason must be null for resolved constructor resolution");
                }
            } else {
                Objects.requireNonNull(detailReason, "detailReason must not be null for failed constructor resolution");
            }
        }
    }

    static @NotNull Resolution resolveConstructor(
            @NotNull ClassRegistry classRegistry,
            @NotNull ScopeTypeMeta receiverTypeMeta,
            @NotNull List<GdType> argumentTypes
    ) {
        Objects.requireNonNull(classRegistry, "classRegistry must not be null");
        Objects.requireNonNull(receiverTypeMeta, "receiverTypeMeta must not be null");
        Objects.requireNonNull(argumentTypes, "argumentTypes must not be null");
        return switch (receiverTypeMeta.kind()) {
            case GLOBAL_ENUM -> failed(
                    receiverTypeMeta.declaration(),
                    null,
                    "Type meta '" + receiverTypeMeta.displayName() + "' does not support constructor calls"
            );
            case ENGINE_CLASS -> resolveEngineConstructor(classRegistry, receiverTypeMeta, argumentTypes);
            case GDCC_CLASS -> resolveGdccConstructor(classRegistry, receiverTypeMeta, argumentTypes);
            case BUILTIN -> resolveBuiltinConstructor(classRegistry, receiverTypeMeta, argumentTypes);
        };
    }

    private static @NotNull Resolution resolveEngineConstructor(
            @NotNull ClassRegistry classRegistry,
            @NotNull ScopeTypeMeta receiverTypeMeta,
            @NotNull List<GdType> argumentTypes
    ) {
        if (!(resolveDeclaredClass(classRegistry, receiverTypeMeta) instanceof ExtensionGdClass engineClass)) {
            return failed(
                    receiverTypeMeta.declaration(),
                    ScopeOwnerKind.ENGINE,
                    "Engine constructor receiver '" + receiverTypeMeta.displayName() + "' has malformed declaration metadata"
            );
        }
        if (!engineClass.isInstantiable()) {
            return failed(
                    engineClass,
                    ScopeOwnerKind.ENGINE,
                    "Engine class '" + receiverTypeMeta.displayName() + "' is not instantiable"
            );
        }
        if (!argumentTypes.isEmpty()) {
            return failed(
                    engineClass,
                    ScopeOwnerKind.ENGINE,
                    "Engine class constructor '" + receiverTypeMeta.displayName() + ".new' accepts no arguments"
            );
        }
        return resolved(engineClass, ScopeOwnerKind.ENGINE);
    }

    private static @NotNull Resolution resolveGdccConstructor(
            @NotNull ClassRegistry classRegistry,
            @NotNull ScopeTypeMeta receiverTypeMeta,
            @NotNull List<GdType> argumentTypes
    ) {
        var classDef = resolveDeclaredClass(classRegistry, receiverTypeMeta);
        if (classDef == null) {
            return failed(
                    receiverTypeMeta.declaration(),
                    ScopeOwnerKind.GDCC,
                    "Constructor receiver '" + receiverTypeMeta.displayName() + "' has unavailable class metadata"
            );
        }
        var constructors = classDef.getFunctions().stream()
                .filter(function -> function.getName().equals("_init") && !function.isStatic())
                .toList();
        if (constructors.isEmpty()) {
            if (argumentTypes.isEmpty()) {
                return resolved(defaultDeclarationSite(receiverTypeMeta, classDef), ScopeOwnerKind.GDCC);
            }
            return failed(
                    defaultDeclarationSite(receiverTypeMeta, classDef),
                    ScopeOwnerKind.GDCC,
                    "Class '" + receiverTypeMeta.displayName() + "' has no matching constructor overload for "
                            + renderArgumentTypes(argumentTypes)
            );
        }
        return chooseConstructor(classRegistry, receiverTypeMeta, constructors, argumentTypes, ScopeOwnerKind.GDCC);
    }

    private static @NotNull Resolution resolveBuiltinConstructor(
            @NotNull ClassRegistry classRegistry,
            @NotNull ScopeTypeMeta receiverTypeMeta,
            @NotNull List<GdType> argumentTypes
    ) {
        var builtinClass = resolveBuiltinStaticOwner(classRegistry, receiverTypeMeta);
        if (builtinClass == null) {
            return failed(
                    receiverTypeMeta.declaration(),
                    ScopeOwnerKind.BUILTIN,
                    "Builtin constructor receiver '" + receiverTypeMeta.displayName()
                            + "' is not backed by builtin metadata"
            );
        }
        if (builtinClass.constructors().isEmpty()) {
            return failed(
                    builtinClass,
                    ScopeOwnerKind.BUILTIN,
                    "Builtin type '" + receiverTypeMeta.displayName() + "' has no constructor metadata"
            );
        }
        return chooseConstructor(
                classRegistry,
                receiverTypeMeta,
                builtinClass.constructors(),
                argumentTypes,
                ScopeOwnerKind.BUILTIN
        );
    }

    private static @NotNull Resolution chooseConstructor(
            @NotNull ClassRegistry classRegistry,
            @NotNull ScopeTypeMeta receiverTypeMeta,
            @NotNull List<? extends FunctionDef> constructors,
            @NotNull List<GdType> argumentTypes,
            @NotNull ScopeOwnerKind ownerKind
    ) {
        var applicable = constructors.stream()
                .filter(constructor -> matchesCallableArguments(classRegistry, constructor, argumentTypes))
                .toList();
        if (applicable.size() == 1) {
            return resolved(applicable.getFirst(), ownerKind);
        }
        if (applicable.size() > 1) {
            return failed(
                    defaultDeclarationSite(receiverTypeMeta, null),
                    ownerKind,
                    "Ambiguous constructor overload for '" + receiverTypeMeta.displayName() + ".new': "
                            + renderCallableSignatures(applicable)
            );
        }
        var detailReason = constructors.isEmpty()
                ? "Type '" + receiverTypeMeta.displayName() + "' exposes no constructors"
                : "No applicable constructor overload for '" + receiverTypeMeta.displayName() + ".new': "
                  + buildCallableMismatchReason(classRegistry, constructors.getFirst(), argumentTypes)
                  + ". candidates: " + renderCallableSignatures(constructors);
        return failed(defaultDeclarationSite(receiverTypeMeta, null), ownerKind, detailReason);
    }

    private static boolean matchesCallableArguments(
            @NotNull ClassRegistry classRegistry,
            @NotNull FunctionDef callable,
            @NotNull List<GdType> argumentTypes
    ) {
        var parameters = callable.getParameters();
        var fixedCount = parameters.size();
        var providedCount = argumentTypes.size();
        if (providedCount < fixedCount && !canOmitTrailingParameters(parameters, providedCount)) {
            return false;
        }
        if (!callable.isVararg() && providedCount > fixedCount) {
            return false;
        }
        var fixedPrefixCount = Math.min(providedCount, fixedCount);
        for (var index = 0; index < fixedPrefixCount; index++) {
            if (!FrontendVariantBoundaryCompatibility.isFrontendBoundaryCompatible(
                    classRegistry,
                    argumentTypes.get(index),
                    parameters.get(index).getType()
            )) {
                return false;
            }
        }
        // Constructor vararg tails follow the same Variant-packing rule as ordinary calls.
        return true;
    }

    private static @NotNull String buildCallableMismatchReason(
            @NotNull ClassRegistry classRegistry,
            @NotNull FunctionDef callable,
            @NotNull List<GdType> argumentTypes
    ) {
        var parameters = callable.getParameters();
        var fixedCount = parameters.size();
        var providedCount = argumentTypes.size();
        if (providedCount < fixedCount && !canOmitTrailingParameters(parameters, providedCount)) {
            var missingParameterIndex = firstMissingRequiredParameter(parameters, providedCount);
            return "missing required parameter #" + (missingParameterIndex + 1) + " ('"
                    + parameters.get(missingParameterIndex).getName() + "')";
        }
        if (!callable.isVararg() && providedCount > fixedCount) {
            return "expected " + fixedCount + " arguments, got " + providedCount;
        }
        var fixedPrefixCount = Math.min(providedCount, fixedCount);
        for (var index = 0; index < fixedPrefixCount; index++) {
            var argumentType = argumentTypes.get(index);
            var parameter = parameters.get(index);
            if (!FrontendVariantBoundaryCompatibility.isFrontendBoundaryCompatible(
                    classRegistry,
                    argumentType,
                    parameter.getType()
            )) {
                return "argument #" + (index + 1) + " of type '" + argumentType.getTypeName()
                        + "' is not assignable to parameter '" + parameter.getName()
                        + "' of type '" + parameter.getType().getTypeName() + "'";
            }
        }
        return "no compatible signature found";
    }

    private static boolean canOmitTrailingParameters(
            @NotNull List<? extends ParameterDef> parameters,
            int providedCount
    ) {
        for (var index = providedCount; index < parameters.size(); index++) {
            if (parameters.get(index).getDefaultValueFunc() == null) {
                return false;
            }
        }
        return true;
    }

    private static int firstMissingRequiredParameter(
            @NotNull List<? extends ParameterDef> parameters,
            int providedCount
    ) {
        for (var index = providedCount; index < parameters.size(); index++) {
            if (parameters.get(index).getDefaultValueFunc() == null) {
                return index;
            }
        }
        return providedCount;
    }

    private static @NotNull String renderCallableSignatures(@NotNull List<? extends FunctionDef> callables) {
        var joiner = new StringJoiner("; ");
        for (var callable : callables) {
            var argsJoiner = new StringJoiner(", ");
            for (var parameter : callable.getParameters()) {
                argsJoiner.add(parameter.getType().getTypeName());
            }
            if (callable.isVararg()) {
                argsJoiner.add("...");
            }
            joiner.add(callable.getName() + "(" + argsJoiner + ")");
        }
        return joiner.toString();
    }

    private static @NotNull String renderArgumentTypes(@NotNull List<GdType> argumentTypes) {
        var joiner = new StringJoiner(", ", "(", ")");
        for (var argumentType : argumentTypes) {
            joiner.add(argumentType.getTypeName());
        }
        return joiner.toString();
    }

    private static @Nullable ClassDef resolveDeclaredClass(
            @NotNull ClassRegistry classRegistry,
            @NotNull ScopeTypeMeta receiverTypeMeta
    ) {
        if (receiverTypeMeta.declaration() instanceof ClassDef classDef) {
            return classDef;
        }
        if (receiverTypeMeta.instanceType() instanceof GdObjectType objectType) {
            return classRegistry.getClassDef(objectType);
        }
        return null;
    }

    private static @Nullable ExtensionBuiltinClass resolveBuiltinStaticOwner(
            @NotNull ClassRegistry classRegistry,
            @NotNull ScopeTypeMeta receiverTypeMeta
    ) {
        if (receiverTypeMeta.declaration() instanceof ExtensionBuiltinClass builtinClass) {
            return builtinClass;
        }
        return classRegistry.findBuiltinClass(receiverTypeMeta.canonicalName());
    }

    private static @Nullable Object defaultDeclarationSite(
            @NotNull ScopeTypeMeta receiverTypeMeta,
            @Nullable Object fallback
    ) {
        return receiverTypeMeta.declaration() != null ? receiverTypeMeta.declaration() : fallback;
    }

    private static @NotNull Resolution resolved(@Nullable Object declarationSite, @Nullable ScopeOwnerKind ownerKind) {
        return new Resolution(FrontendCallResolutionStatus.RESOLVED, declarationSite, ownerKind, null);
    }

    private static @NotNull Resolution failed(
            @Nullable Object declarationSite,
            @Nullable ScopeOwnerKind ownerKind,
            @NotNull String detailReason
    ) {
        return new Resolution(
                FrontendCallResolutionStatus.FAILED,
                declarationSite,
                ownerKind,
                detailReason
        );
    }
}
