package dev.superice.gdcc.frontend.sema.analyzer.support;

import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdcc.type.GdType;
import dev.superice.gdcc.type.GdVariantType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Shared frontend-only compatibility rule for stable `Variant` boundaries.
///
/// The helper is intentionally narrow:
/// - it decides only whether a source/target pair is accepted at the frontend semantic boundary
/// - it distinguishes direct flow from explicit pack/unpack edges so lowering can reuse the same rule later
/// - it does not emit diagnostics and does not weaken backend/global `ClassRegistry.checkAssignable(...)`
public final class FrontendVariantBoundaryCompatibility {
    public enum Decision {
        ALLOW_DIRECT,
        ALLOW_WITH_PACK,
        ALLOW_WITH_UNPACK,
        REJECT;

        public boolean allows() {
            return this != REJECT;
        }
    }

    private FrontendVariantBoundaryCompatibility() {
    }

    /// Frontend semantic boundary rule:
    /// - exact `Variant` target accepts any stable source, boxing concrete values when needed
    /// - stable `Variant` source may flow into a concrete target and will be unboxed later
    /// - all remaining pairs stay on the strict shared assignability contract
    public static @NotNull Decision determineFrontendBoundaryDecision(
            @NotNull ClassRegistry classRegistry,
            @NotNull GdType sourceType,
            @NotNull GdType targetType
    ) {
        var registry = Objects.requireNonNull(classRegistry, "classRegistry must not be null");
        var source = Objects.requireNonNull(sourceType, "sourceType must not be null");
        var target = Objects.requireNonNull(targetType, "targetType must not be null");
        if (target instanceof GdVariantType) {
            return source instanceof GdVariantType ? Decision.ALLOW_DIRECT : Decision.ALLOW_WITH_PACK;
        }
        if (source instanceof GdVariantType) {
            return Decision.ALLOW_WITH_UNPACK;
        }
        return registry.checkAssignable(source, target) ? Decision.ALLOW_DIRECT : Decision.REJECT;
    }

    public static boolean isFrontendBoundaryCompatible(
            @NotNull ClassRegistry classRegistry,
            @NotNull GdType sourceType,
            @NotNull GdType targetType
    ) {
        return determineFrontendBoundaryDecision(classRegistry, sourceType, targetType).allows();
    }
}
