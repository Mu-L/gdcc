package dev.superice.gdcc.frontend.sema;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// One resolved frontend binding fact attached to an AST use site.
///
/// The current framework only stores symbol category plus declaration provenance. Usage semantics
/// such as read/write/call are intentionally deferred, so assignment left-hand sites and ordinary
/// reads currently share the same binding container shape.
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
