package dev.superice.gdcc.frontend.lowering.cfg.item;

import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdcc.util.StringUtil;
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
public record CallItem(
        @NotNull Node callAnchor,
        @NotNull String callableName,
        @Nullable String receiverValueIdOrNull,
        @NotNull List<String> argumentValueIds,
        @NotNull String resultValueId
) implements ValueOpItem {
    public CallItem {
        Objects.requireNonNull(callAnchor, "callAnchor must not be null");
        callableName = StringUtil.requireNonBlank(callableName, "callableName");
        receiverValueIdOrNull = FrontendCfgItemSupport.validateOptionalValueId(
                receiverValueIdOrNull,
                "receiverValueIdOrNull"
        );
        argumentValueIds = FrontendCfgItemSupport.copyValueIds(argumentValueIds, "argumentValueIds");
        resultValueId = FrontendCfgGraph.validateValueId(resultValueId, "resultValueId");
    }

    @Override
    public @NotNull Node anchor() {
        return callAnchor;
    }

    @Override
    public @NotNull String resultValueIdOrNull() {
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
}
