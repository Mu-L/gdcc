package dev.superice.gdcc.frontend.sema;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// One resolved frontend binding fact attached to an AST use site.
///
/// The current framework only needs a stable container shape. Later binder phases can enrich
/// the declaration site and other metadata without changing the side-table topology.
public record FrontendBinding(
        @NotNull String symbolName,
        @NotNull FrontendBindingKind kind,
        @Nullable Object declarationSite
) {
    public FrontendBinding {
        Objects.requireNonNull(symbolName, "symbolName must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
    }
}
