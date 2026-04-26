package gd.script.gdcc.frontend.lowering.cfg.item;

import gd.script.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdparser.frontend.ast.Expression;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/// Value-producing item for generic source expressions that still lower through the opaque route.
///
/// The exact lowering of many simple expression kinds is handled by `FrontendLoweringBodyInsnPass`, but this item no
/// longer hides nested special operations as one subtree black box. `operandValueIds` now records the
/// already-materialized child values in source order, while `expression` keeps the original generic
/// root so body lowering can finish the operator-specific materialization without re-lowering
/// those children.
public record OpaqueExprValueItem(
        @NotNull Expression expression,
        @NotNull List<String> operandValueIds,
        @NotNull String resultValueId
) implements ValueOpItem {
    public OpaqueExprValueItem {
        Objects.requireNonNull(expression, "expression must not be null");
        operandValueIds = FrontendCfgItemSupport.copyValueIds(operandValueIds, "operandValueIds");
        resultValueId = FrontendCfgGraph.validateValueId(resultValueId, "resultValueId");
    }

    public OpaqueExprValueItem(@NotNull Expression expression, @NotNull String resultValueId) {
        this(expression, List.of(), resultValueId);
    }

    @Override
    public @NotNull Node anchor() {
        return expression;
    }

    @Override
    public @NotNull String resultValueIdOrNull() {
        return resultValueId;
    }
}
