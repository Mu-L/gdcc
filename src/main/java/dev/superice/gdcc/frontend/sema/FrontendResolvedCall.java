package dev.superice.gdcc.frontend.sema;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Placeholder call-resolution fact for the future body analyzer.
public record FrontendResolvedCall(@NotNull String callableName) {
    public FrontendResolvedCall {
        Objects.requireNonNull(callableName, "callableName must not be null");
    }
}
