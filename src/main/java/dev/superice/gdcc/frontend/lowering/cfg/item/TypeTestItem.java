package dev.superice.gdcc.frontend.lowering.cfg.item;

import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdparser.frontend.ast.Node;
import dev.superice.gdparser.frontend.ast.TypeTestExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/// Explicit `is` / type-test placeholder.
///
/// The item consumes one operand value and publishes one result value representing the source-level
/// test outcome. Later lowering remains free to decide how that outcome is materialized for the
/// target backend.
public record TypeTestItem(
        @NotNull TypeTestExpression expression,
        @NotNull String operandValueId,
        @NotNull String resultValueId
) implements ValueOpItem {
    public TypeTestItem {
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
