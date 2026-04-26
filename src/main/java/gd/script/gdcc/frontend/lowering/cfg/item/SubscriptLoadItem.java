package gd.script.gdcc.frontend.lowering.cfg.item;

import gd.script.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import gd.script.gdcc.util.StringUtil;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Subscript/index read placeholder.
///
/// The item separates the already-materialized base value from the already-materialized index
/// arguments, preserving source order through `argumentValueIds` while still publishing one explicit
/// `resultValueId` for downstream control-flow and lowering steps. `memberNameOrNull` is populated
/// only for `AttributeSubscriptStep`, where the step also carries the property name whose value is
/// indexed.
public record SubscriptLoadItem(
        @NotNull Node subscriptAnchor,
        @Nullable String memberNameOrNull,
        @NotNull String baseValueId,
        @NotNull List<String> argumentValueIds,
        @NotNull String resultValueId
) implements ValueOpItem {
    public SubscriptLoadItem {
        Objects.requireNonNull(subscriptAnchor, "subscriptAnchor must not be null");
        memberNameOrNull = memberNameOrNull == null
                ? null
                : StringUtil.requireNonBlank(memberNameOrNull, "memberNameOrNull");
        baseValueId = FrontendCfgGraph.validateValueId(baseValueId, "baseValueId");
        argumentValueIds = FrontendCfgItemSupport.copyValueIds(argumentValueIds, "argumentValueIds");
        resultValueId = FrontendCfgGraph.validateValueId(resultValueId, "resultValueId");
    }

    @Override
    public @NotNull Node anchor() {
        return subscriptAnchor;
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
