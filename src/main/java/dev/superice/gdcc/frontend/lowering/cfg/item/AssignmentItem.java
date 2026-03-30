package dev.superice.gdcc.frontend.lowering.cfg.item;

import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdparser.frontend.ast.AssignmentExpression;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// Assignment/store commit placeholder for the frontend CFG.
///
/// `rhsValueId` points to the already-evaluated right-hand side. `resultValueIdOrNull` remains
/// optional because future lowering may need to preserve assignment-as-expression semantics for some
/// source forms, while statement-root assignments only commit state and publish no new value.
public record AssignmentItem(
        @NotNull AssignmentExpression assignment,
        @NotNull String rhsValueId,
        @Nullable String resultValueIdOrNull
) implements ValueOpItem {
    public AssignmentItem {
        Objects.requireNonNull(assignment, "assignment must not be null");
        rhsValueId = FrontendCfgGraph.validateValueId(rhsValueId, "rhsValueId");
        resultValueIdOrNull = FrontendCfgItemSupport.validateOptionalValueId(
                resultValueIdOrNull,
                "resultValueIdOrNull"
        );
    }

    @Override
    public @NotNull Node anchor() {
        return assignment;
    }

    @Override
    public @NotNull List<String> operandValueIds() {
        return List.of(rhsValueId);
    }
}
