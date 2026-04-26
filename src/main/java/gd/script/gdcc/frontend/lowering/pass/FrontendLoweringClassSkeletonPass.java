package gd.script.gdcc.frontend.lowering.pass;

import gd.script.gdcc.frontend.lowering.FrontendLoweringContext;
import gd.script.gdcc.frontend.lowering.FrontendLoweringPass;
import gd.script.gdcc.lir.LirModule;
import org.jetbrains.annotations.NotNull;

/// Lowering pass that emits the backend-facing module shell directly from published frontend
/// class skeletons.
///
/// The pass reuses the already-built `LirClassDef` objects from `FrontendModuleSkeleton` instead
/// of rebuilding or cloning them.
public final class FrontendLoweringClassSkeletonPass implements FrontendLoweringPass {
    @Override
    public void run(@NotNull FrontendLoweringContext context) {
        var moduleSkeleton = context.requireAnalysisData().moduleSkeleton();
        context.publishLirModule(new LirModule(
                moduleSkeleton.moduleName(),
                moduleSkeleton.allClassDefs()
        ));
    }
}
