package dev.superice.gdcc.frontend.lowering.cfg;

import org.jetbrains.annotations.NotNull;

/// Region for one `while` loop.
///
/// `entryId()` aliases `conditionEntryId` so `continue` lowering can target the loop's
/// condition-evaluation entry directly.
public record FrontendWhileRegion(
        @NotNull String conditionEntryId,
        @NotNull String bodyEntryId,
        @NotNull String exitId
) implements FrontendCfgRegion {
    public FrontendWhileRegion {
        conditionEntryId = FrontendCfgGraph.validateNodeId(conditionEntryId, "conditionEntryId");
        bodyEntryId = FrontendCfgGraph.validateNodeId(bodyEntryId, "bodyEntryId");
        exitId = FrontendCfgGraph.validateNodeId(exitId, "exitId");
    }

    @Override
    public @NotNull String entryId() {
        return conditionEntryId;
    }
}
