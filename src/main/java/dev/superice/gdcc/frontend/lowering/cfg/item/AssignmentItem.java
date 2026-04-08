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
/// commit state and publish no new value. If the target also participates in writable-route
/// lowering, `writableRoutePayloadOrNull` publishes that frozen owner/leaf/writeback shape
/// alongside the legacy operand list.
public record AssignmentItem(
        @NotNull AssignmentExpression assignment,
        @NotNull List<String> targetOperandValueIds,
        @NotNull String rhsValueId,
        @Nullable String resultValueIdOrNull,
        @Nullable FrontendWritableRoutePayload writableRoutePayloadOrNull
) implements ValueOpItem {
    public AssignmentItem(
            @NotNull AssignmentExpression assignment,
            @NotNull List<String> targetOperandValueIds,
            @NotNull String rhsValueId,
            @Nullable String resultValueIdOrNull
    ) {
        this(assignment, targetOperandValueIds, rhsValueId, resultValueIdOrNull, null);
    }

    public AssignmentItem {
        Objects.requireNonNull(assignment, "assignment must not be null");
        targetOperandValueIds = FrontendCfgItemSupport.copyValueIds(targetOperandValueIds, "targetOperandValueIds");
        rhsValueId = FrontendCfgGraph.validateValueId(rhsValueId, "rhsValueId");
        resultValueIdOrNull = FrontendCfgItemSupport.validateOptionalValueId(
                resultValueIdOrNull,
                "resultValueIdOrNull"
        );
        if (writableRoutePayloadOrNull != null && writableRoutePayloadOrNull.routeAnchor() != assignment) {
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
}
