package dev.superice.gdcc.frontend.lowering.cfg.item;

import dev.superice.gdparser.frontend.ast.Node;
import dev.superice.gdparser.frontend.ast.VariableDeclaration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// Explicit local declaration commit inside a sequence.
///
/// The initializer expression, when present, is modeled as a separate preceding value op. This item
/// therefore consumes the already-materialized initializer id through `initializerValueIdOrNull`
/// instead of embedding the expression subtree again. Declaring the local does not itself publish a
/// new value id, because it represents a state commit rather than a value expression result.
public record LocalDeclarationItem(
        @NotNull VariableDeclaration declaration,
        @Nullable String initializerValueIdOrNull
) implements ValueOpItem {
    public LocalDeclarationItem {
        Objects.requireNonNull(declaration, "declaration must not be null");
        initializerValueIdOrNull = FrontendCfgItemSupport.validateOptionalValueId(
                initializerValueIdOrNull,
                "initializerValueIdOrNull"
        );
    }

    @Override
    public @NotNull Node anchor() {
        return declaration;
    }

    @Override
    public @Nullable String resultValueIdOrNull() {
        return null;
    }

    @Override
    public @NotNull List<String> operandValueIds() {
        return initializerValueIdOrNull == null ? List.of() : List.of(initializerValueIdOrNull);
    }
}
