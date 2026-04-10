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
/// `targetOperandValueIds` freezes the already-evaluated receiver/index operands of the assignment
/// target in source order, while `rhsValueId` points to the already-evaluated right-hand side.
/// `resultValueIdOrNull` remains optional because future lowering may need to preserve
/// assignment-as-expression semantics for some source forms, while statement-root assignments only
/// commit state and publish no new value.
///
/// For every assignment target currently allowed into lowering, `writableRoutePayload` is mandatory.
/// Step 4 hard-cut final-store lowering to consume only this frozen route shape; the legacy operand
/// list remains solely for source-order sequencing and compound current-value reads.
public record AssignmentItem(
        @NotNull AssignmentExpression assignment,
        @NotNull List<String> targetOperandValueIds,
        @NotNull String rhsValueId,
        @Nullable String resultValueIdOrNull,
        @NotNull FrontendWritableRoutePayload writableRoutePayload
) implements ValueOpItem {
    public AssignmentItem {
        Objects.requireNonNull(assignment, "assignment must not be null");
        targetOperandValueIds = FrontendCfgItemSupport.copyValueIds(targetOperandValueIds, "targetOperandValueIds");
        rhsValueId = FrontendCfgGraph.validateValueId(rhsValueId, "rhsValueId");
        resultValueIdOrNull = FrontendCfgItemSupport.validateOptionalValueId(
                resultValueIdOrNull,
                "resultValueIdOrNull"
        );
        var actualWritableRoutePayload = Objects.requireNonNull(
                writableRoutePayload,
                "writableRoutePayload must not be null"
        );
        if (actualWritableRoutePayload.routeAnchor() != assignment) {
            throw new IllegalArgumentException("AssignmentItem writable route anchor must match assignment");
        }
    }

    @Override
    public @NotNull Node anchor() {
        return assignment;
    }

    @Override
    public @NotNull List<String> operandValueIds() {
        var operands = new java.util.ArrayList<String>(targetOperandValueIds.size() + 1);
        operands.addAll(targetOperandValueIds);
        operands.add(rhsValueId);
        return List.copyOf(operands);
    }

    @Override
    public boolean hasStandaloneMaterializationSlot() {
        return resultValueIdOrNull != null;
    }
}
