package dev.superice.gdcc.frontend.lowering.cfg.item;

import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdcc.util.StringUtil;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// Property/member read placeholder for an already-resolved member route.
///
/// `memberAnchor` is normally the published `AttributePropertyStep`, while `memberName` freezes the
/// exact source member token used by this read so later lowering does not need to peel it back out of
/// the surrounding attribute chain.
/// Ordinary instance reads carry `baseValueIdOrNull`, while type-meta static loads such as
/// `Vector3.ZERO` or `Color.RED` intentionally keep it null because the chain head is never
/// materialized as an ordinary runtime receiver value.
public record MemberLoadItem(
        @NotNull Node memberAnchor,
        @NotNull String memberName,
        @Nullable String baseValueIdOrNull,
        @NotNull String resultValueId
) implements ValueOpItem {
    public MemberLoadItem {
        Objects.requireNonNull(memberAnchor, "memberAnchor must not be null");
        memberName = StringUtil.requireNonBlank(memberName, "memberName");
        if (baseValueIdOrNull != null) {
            baseValueIdOrNull = FrontendCfgGraph.validateValueId(baseValueIdOrNull, "baseValueIdOrNull");
        }
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
        return baseValueIdOrNull == null ? List.of() : List.of(baseValueIdOrNull);
    }
}
