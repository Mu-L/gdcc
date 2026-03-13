package dev.superice.gdcc.frontend.sema;

import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Shared read-only ownership protocol implemented directly by top-level and inner class relation
/// records.
///
/// This keeps source-facing naming, canonical identity, and AST ownership on the relation objects
/// themselves, so later frontend phases can consume one stable protocol without allocating
/// temporary adapter records.
public interface FrontendOwnedClassRelation {
    @NotNull Node lexicalOwner();

    @NotNull Node astOwner();

    @NotNull String sourceName();

    @NotNull String canonicalName();

    @NotNull LirClassDef classDef();

    static void validateOwnedRelation(
            @NotNull Node lexicalOwner,
            @NotNull Node astOwner,
            @NotNull String sourceName,
            @NotNull String canonicalName,
            @NotNull LirClassDef classDef,
            @NotNull String relationKind
    ) {
        Objects.requireNonNull(lexicalOwner, "lexicalOwner must not be null");
        Objects.requireNonNull(astOwner, "astOwner must not be null");
        Objects.requireNonNull(sourceName, "sourceName must not be null");
        Objects.requireNonNull(canonicalName, "canonicalName must not be null");
        Objects.requireNonNull(classDef, "classDef must not be null");
        Objects.requireNonNull(relationKind, "relationKind must not be null");
        if (!canonicalName.equals(classDef.getName())) {
            throw new IllegalArgumentException(
                    relationKind + " canonicalName must match classDef.getName()"
            );
        }
    }
}
