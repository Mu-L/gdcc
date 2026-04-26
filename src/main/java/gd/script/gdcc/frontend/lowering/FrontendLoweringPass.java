package gd.script.gdcc.frontend.lowering;

import org.jetbrains.annotations.NotNull;

/// Shared lowering pass protocol used by the fixed frontend lowering pipeline.
///
/// Although implementations now live under `frontend.lowering.pass`, the protocol still exists only
/// to support the internal pass manager pipeline rather than as an external extension point.
public interface FrontendLoweringPass {
    void run(@NotNull FrontendLoweringContext context);
}
