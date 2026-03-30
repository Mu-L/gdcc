package dev.superice.gdcc.frontend.lowering.cfg.item;

import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdparser.frontend.ast.Expression;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/// Property/member read placeholder for a previously-evaluated base value.
///
/// `expression` remains the full source root so diagnostics and later semantic lookups still anchor
/// to the original AST, while `baseValueId` and `resultValueId` make the data-flow explicit inside
/// the sequence.
public record MemberLoadItem(
        @NotNull Expression expression,
        @NotNull String baseValueId,
        @NotNull String resultValueId
) implements ValueOpItem {
    public MemberLoadItem {
        Objects.requireNonNull(expression, "expression must not be null");
        baseValueId = FrontendCfgGraph.validateValueId(baseValueId, "baseValueId");
        resultValueId = FrontendCfgGraph.validateValueId(resultValueId, "resultValueId");
    }

    @Override
    public @NotNull Node anchor() {
        return expression;
    }

    @Override
    public @NotNull String resultValueIdOrNull() {
        return resultValueId;
    }

    @Override
    public @NotNull List<String> operandValueIds() {
        return List.of(baseValueId);
    }
}
