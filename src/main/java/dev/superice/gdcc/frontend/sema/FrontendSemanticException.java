package dev.superice.gdcc.frontend.sema;

import dev.superice.gdcc.frontend.diagnostic.FrontendDiagnostic;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/// Exception thrown when frontend semantic analysis must fail-fast.
public final class FrontendSemanticException extends RuntimeException {
    private final @NotNull List<FrontendDiagnostic> diagnostics;

    public FrontendSemanticException(@NotNull String message, @NotNull List<FrontendDiagnostic> diagnostics) {
        super(message);
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    }

    public @NotNull List<FrontendDiagnostic> diagnostics() {
        return diagnostics;
    }
}
