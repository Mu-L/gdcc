package gd.script.gdcc.frontend.lowering;

import gd.script.gdcc.frontend.diagnostic.DiagnosticManager;
import gd.script.gdcc.frontend.lowering.pass.FrontendLoweringAnalysisPass;
import gd.script.gdcc.frontend.lowering.pass.FrontendLoweringBodyInsnPass;
import gd.script.gdcc.frontend.lowering.pass.FrontendLoweringBuildCfgPass;
import gd.script.gdcc.frontend.lowering.pass.FrontendLoweringClassSkeletonPass;
import gd.script.gdcc.frontend.lowering.pass.FrontendLoweringFunctionPreparationPass;
import gd.script.gdcc.frontend.parse.FrontendModule;
import gd.script.gdcc.lir.LirModule;
import gd.script.gdcc.scope.ClassRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// Public frontend lowering entrypoint that executes the fixed lowering pass pipeline.
///
/// The current pipeline consumes a `FrontendModule`, runs compile-ready semantic analysis, and
/// emits a `LirModule` whose executable functions already contain frontend-lowered basic blocks.
/// Supported property initializer helpers travel through the same CFG/body lowering pipeline and
/// reach backend as real executable helper bodies rather than shell-only stubs.
public final class FrontendLoweringPassManager {
    private final @NotNull List<FrontendLoweringPass> passes;

    public FrontendLoweringPassManager() {
        this(List.of(
                new FrontendLoweringAnalysisPass(),
                new FrontendLoweringClassSkeletonPass(),
                new FrontendLoweringFunctionPreparationPass(),
                new FrontendLoweringBuildCfgPass(),
                new FrontendLoweringBodyInsnPass()
        ));
    }

    FrontendLoweringPassManager(@NotNull List<FrontendLoweringPass> passes) {
        this.passes = List.copyOf(Objects.requireNonNull(passes, "passes must not be null"));
    }

    public @Nullable LirModule lower(
            @NotNull FrontendModule module,
            @NotNull ClassRegistry classRegistry,
            @NotNull DiagnosticManager diagnosticManager
    ) {
        return lowerToContext(module, classRegistry, diagnosticManager).lirModuleOrNull();
    }

    /// Package-local test hook that exposes the final lowering context without widening the public
    /// lowering API. The public entrypoint still returns only the published `LirModule`.
    @NotNull FrontendLoweringContext lowerToContext(
            @NotNull FrontendModule module,
            @NotNull ClassRegistry classRegistry,
            @NotNull DiagnosticManager diagnosticManager
    ) {
        var context = new FrontendLoweringContext(module, classRegistry, diagnosticManager);
        for (var pass : passes) {
            if (context.isStopRequested()) {
                break;
            }
            pass.run(context);
        }
        return context;
    }
}
