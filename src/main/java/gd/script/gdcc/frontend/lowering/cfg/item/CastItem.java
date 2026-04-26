package gd.script.gdcc.frontend.lowering.cfg.item;

import gd.script.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdparser.frontend.ast.CastExpression;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/// Explicit cast placeholder for one already-materialized operand value.
///
/// This item keeps the original `CastExpression` root for diagnostics and semantic lookup while
/// making the cast data-flow explicit through `operandValueId` and `resultValueId`.
public record CastItem(
        @NotNull CastExpression expression,
        @NotNull String operandValueId,
        @NotNull String resultValueId
) implements ValueOpItem {
    public CastItem {
        Objects.requireNonNull(expression, "expression must not be null");
        operandValueId = FrontendCfgGraph.validateValueId(operandValueId, "operandValueId");
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
        return List.of(operandValueId);
    }
}
