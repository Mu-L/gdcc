package dev.superice.gdcc.frontend.lowering;

import dev.superice.gdcc.type.GdArrayType;
import dev.superice.gdcc.type.GdDictionaryType;
import dev.superice.gdcc.type.GdObjectType;
import dev.superice.gdcc.type.GdPrimitiveType;
import dev.superice.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Shared truth source for which statically known carrier families still need reverse writeback.
///
/// The contract comes from `doc/gdcc_type_system.md` and is consumed by Step-4 assignment lowering,
/// mutating-receiver writeback, and later runtime-gated routes:
/// - shared/reference carriers (`Array`, `Dictionary`, `Object`, primitive slots) do not write back
/// - value-semantic builtin carriers do write back
/// - `Variant` currently returns `true` here because the static shortcut only skips families already
///   proven shared; the runtime helper refines the unknown branch later
public final class FrontendWritableTypeWritebackSupport {
    private FrontendWritableTypeWritebackSupport() {
    }

    public static boolean requiresReverseCommitForCarrierType(@NotNull GdType carrierType) {
        return switch (Objects.requireNonNull(carrierType, "carrierType must not be null")) {
            case GdPrimitiveType _, GdObjectType _, GdArrayType _, GdDictionaryType _ -> false;
            default -> true;
        };
    }
}
