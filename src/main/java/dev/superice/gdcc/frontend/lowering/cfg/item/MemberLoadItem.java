package dev.superice.gdcc.frontend.lowering.cfg.item;

import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/// Property/member read placeholder for a previously-evaluated base value.
///
/// `memberAnchor` is normally the published `AttributePropertyStep`, while `memberName` freezes the
/// exact source member token used by this read so later lowering does not need to peel it back out of
/// the surrounding attribute chain.
public record MemberLoadItem(
        @NotNull Node memberAnchor,
        @NotNull String memberName,
        @NotNull String baseValueId,
        @NotNull String resultValueId
) implements ValueOpItem {
    public MemberLoadItem {
        Objects.requireNonNull(memberAnchor, "memberAnchor must not be null");
        memberName = FrontendCfgItemSupport.requireNonBlank(memberName, "memberName");
        baseValueId = FrontendCfgGraph.validateValueId(baseValueId, "baseValueId");
        resultValueId = FrontendCfgGraph.validateValueId(resultValueId, "resultValueId");
    }

    @Override
    public @NotNull Node anchor() {
        return memberAnchor;
    }

    @Override
    public @NotNull String resultValueIdOrNull() {
        return resultValueId;
    }

    @Override
    public @NotNull List<String> operandValueIds() {
        return List.of(baseValueId);
    }
}
