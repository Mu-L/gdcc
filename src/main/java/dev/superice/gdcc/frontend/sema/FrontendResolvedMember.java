package dev.superice.gdcc.frontend.sema;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Placeholder member-resolution fact for the future body analyzer.
///
/// The initial framework keeps this intentionally small: the analyzer can already expose a
/// dedicated side table today, while later phases decide how much owner/type detail to store.
public record FrontendResolvedMember(
        @NotNull String memberName,
        @NotNull FrontendBindingKind bindingKind
) {
    public FrontendResolvedMember {
        Objects.requireNonNull(memberName, "memberName must not be null");
        Objects.requireNonNull(bindingKind, "bindingKind must not be null");
    }
}
