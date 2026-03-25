package dev.superice.gdcc.frontend.lowering.pass;

import dev.superice.gdcc.frontend.lowering.FrontendLoweringContext;
import dev.superice.gdcc.frontend.lowering.FrontendLoweringPass;
import dev.superice.gdcc.lir.LirModule;
import org.jetbrains.annotations.NotNull;

/// Lowering pass that emits the backend-facing module shell directly from published frontend
/// class skeletons.
///
/// v1 lowering deliberately reuses the already-built `LirClassDef` objects from
/// `FrontendModuleSkeleton` instead of rebuilding or cloning them. Later passes are expected to
/// continue mutating the same skeleton objects in place when function bodies and CFGs are attached.
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
