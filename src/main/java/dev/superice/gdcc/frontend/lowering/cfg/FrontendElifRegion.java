package dev.superice.gdcc.frontend.lowering.cfg;

import org.jetbrains.annotations.NotNull;

/// Region for one `elif` clause inside an `if` chain.
public record FrontendElifRegion(
        @NotNull String conditionEntryId,
        @NotNull String bodyEntryId,
        @NotNull String nextClauseOrMergeId
) implements FrontendCfgRegion {
    public FrontendElifRegion {
        conditionEntryId = FrontendCfgGraph.validateNodeId(conditionEntryId, "conditionEntryId");
        bodyEntryId = FrontendCfgGraph.validateNodeId(bodyEntryId, "bodyEntryId");
        nextClauseOrMergeId = FrontendCfgGraph.validateNodeId(nextClauseOrMergeId, "nextClauseOrMergeId");
    }

    @Override
    public @NotNull String entryId() {
        return conditionEntryId;
    }
}
