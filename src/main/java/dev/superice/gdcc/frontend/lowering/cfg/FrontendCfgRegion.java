package dev.superice.gdcc.frontend.lowering.cfg;

import org.jetbrains.annotations.NotNull;

/// AST-keyed frontend CFG region published alongside one `FrontendCfgGraph`.
///
/// Regions give later lowering passes stable entry/merge/exit anchors for structured source
/// constructs without forcing them to rediscover those anchors from the raw graph shape.
public sealed interface FrontendCfgRegion
        permits FrontendCfgRegion.BlockRegion,
        FrontendIfRegion,
        FrontendElifRegion,
        FrontendWhileRegion {
    @NotNull String entryId();

    /// Region for one lexical `Block`.
    record BlockRegion(@NotNull String entryId) implements FrontendCfgRegion {
        public BlockRegion {
            entryId = FrontendCfgGraph.validateNodeId(entryId, "entryId");
        }
    }
}
