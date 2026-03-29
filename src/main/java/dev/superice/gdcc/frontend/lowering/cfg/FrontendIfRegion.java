package dev.superice.gdcc.frontend.lowering.cfg;

import org.jetbrains.annotations.NotNull;

/// Region for one `if` statement.
///
/// `entryId()` aliases `conditionEntryId` because the condition-evaluation region is the true
/// entry point of the full `if` chain.
public record FrontendIfRegion(
        @NotNull String conditionEntryId,
        @NotNull String thenEntryId,
        @NotNull String elseOrNextClauseEntryId,
        @NotNull String mergeId
) implements FrontendCfgRegion {
    public FrontendIfRegion {
        conditionEntryId = FrontendCfgGraph.validateNodeId(conditionEntryId, "conditionEntryId");
        thenEntryId = FrontendCfgGraph.validateNodeId(thenEntryId, "thenEntryId");
        elseOrNextClauseEntryId = FrontendCfgGraph.validateNodeId(elseOrNextClauseEntryId, "elseOrNextClauseEntryId");
        mergeId = FrontendCfgGraph.validateNodeId(mergeId, "mergeId");
    }

    @Override
    public @NotNull String entryId() {
        return conditionEntryId;
    }
}
