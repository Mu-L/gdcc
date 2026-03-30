package dev.superice.gdcc.frontend.lowering.cfg.item;

import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdparser.frontend.ast.Expression;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/// Transitional value-producing item for one whole source expression.
///
/// The current frontend CFG migration still treats many nested expression trees as one opaque step.
/// `expression` keeps the original source root, and `resultValueId` gives later items or control-flow
/// nodes one stable handle for the produced value without requiring immediate lowering into finer
/// member/call/subscript operations.
public record OpaqueExprValueItem(
        @NotNull Expression expression,
        @NotNull String resultValueId
) implements ValueOpItem {
    public OpaqueExprValueItem {
        Objects.requireNonNull(expression, "expression must not be null");
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
        return List.of();
    }
}
