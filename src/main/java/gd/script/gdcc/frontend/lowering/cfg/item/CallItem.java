package gd.script.gdcc.frontend.lowering.cfg.item;

import gd.script.gdcc.util.StringUtil;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Call placeholder whose receiver and arguments have already been evaluated.
///
/// `receiverValueIdOrNull` is absent for bare, static, or utility calls. When present it is placed
/// before `argumentValueIds` in `operandValueIds()` so later lowering can consume operands in source
/// call order without reconstructing the receiver shape from the original AST. `callAnchor` is either
/// the bare `CallExpression` root or the owning `AttributeCallStep`, and `callableName` freezes the
/// semantic route name that later lowering should use instead of re-reading the callee subtree.
/// If the receiver also participates in writable-route lowering, `writableRoutePayloadOrNull`
/// publishes that owner/leaf/writeback shape separately from the ordinary call operands. That
/// payload never replaces the dedicated receiver operand slot for ordinary instance-call execution:
/// payload-backed calls must still publish `receiverValueIdOrNull` so body lowering can reuse the
/// already-frozen receiver value instead of re-reading the leaf. Statement-position resolved-void
/// calls are the one supported exception to the usual "call publishes a value" rule: they keep the
/// frozen call route and writable payload but intentionally omit a standalone result slot.
public record CallItem(
        @NotNull Node callAnchor,
        @NotNull String callableName,
        @Nullable String receiverValueIdOrNull,
        @NotNull List<String> argumentValueIds,
        @Nullable String resultValueId,
        @Nullable FrontendWritableRoutePayload writableRoutePayloadOrNull
) implements ValueOpItem {
    public CallItem(
            @NotNull Node callAnchor,
            @NotNull String callableName,
            @Nullable String receiverValueIdOrNull,
            @NotNull List<String> argumentValueIds,
            @Nullable String resultValueId
    ) {
        this(callAnchor, callableName, receiverValueIdOrNull, argumentValueIds, resultValueId, null);
    }

    public CallItem {
        Objects.requireNonNull(callAnchor, "callAnchor must not be null");
        callableName = StringUtil.requireNonBlank(callableName, "callableName");
        receiverValueIdOrNull = FrontendCfgItemSupport.validateOptionalValueId(
                receiverValueIdOrNull,
                "receiverValueIdOrNull"
        );
        argumentValueIds = FrontendCfgItemSupport.copyValueIds(argumentValueIds, "argumentValueIds");
        resultValueId = FrontendCfgItemSupport.validateOptionalValueId(resultValueId, "resultValueId");
        if (writableRoutePayloadOrNull != null && writableRoutePayloadOrNull.routeAnchor() != callAnchor) {
            throw new IllegalArgumentException("CallItem writable route anchor must match callAnchor");
        }
    }

    @Override
    public @NotNull Node anchor() {
        return callAnchor;
    }

    @Override
    public @Nullable String resultValueIdOrNull() {
        return resultValueId;
    }

    @Override
    public @NotNull List<String> operandValueIds() {
        if (receiverValueIdOrNull == null) {
            return argumentValueIds;
        }
        var operands = new ArrayList<String>(1 + argumentValueIds.size());
        operands.add(receiverValueIdOrNull);
        operands.addAll(argumentValueIds);
        return List.copyOf(operands);
    }

    @Override
    public boolean hasStandaloneMaterializationSlot() {
        return resultValueId != null;
    }
}
