package dev.superice.gdcc.frontend.lowering.cfg.item;

import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdparser.frontend.ast.Expression;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Subscript/index read placeholder.
///
/// The item separates the already-materialized base value from the already-materialized index
/// arguments, preserving source order through `argumentValueIds` while still publishing one explicit
/// `resultValueId` for downstream control-flow and lowering steps.
public record SubscriptLoadItem(
        @NotNull Expression expression,
        @NotNull String baseValueId,
        @NotNull List<String> argumentValueIds,
        @NotNull String resultValueId
) implements ValueOpItem {
    public SubscriptLoadItem {
        Objects.requireNonNull(expression, "expression must not be null");
        baseValueId = FrontendCfgGraph.validateValueId(baseValueId, "baseValueId");
        argumentValueIds = FrontendCfgItemSupport.copyValueIds(argumentValueIds, "argumentValueIds");
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
        var operands = new ArrayList<String>(1 + argumentValueIds.size());
        operands.add(baseValueId);
        operands.addAll(argumentValueIds);
        return List.copyOf(operands);
    }
}
