package gd.script.gdcc.frontend.lowering;

import gd.script.gdcc.frontend.diagnostic.DiagnosticManager;
import gd.script.gdcc.frontend.parse.FrontendModule;
import gd.script.gdcc.frontend.sema.FrontendAnalysisData;
import gd.script.gdcc.lir.LirModule;
import gd.script.gdcc.scope.ClassRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// Shared mutable lowering state passed between fixed frontend lowering passes.
///
/// The type is public so pass implementations can live under `frontend.lowering.pass`, but it
/// remains part of the lowering package's internal coordination contract rather than a new public
/// compilation entrypoint.
public final class FrontendLoweringContext {
    private final @NotNull FrontendModule module;
    private final @NotNull ClassRegistry classRegistry;
    private final @NotNull DiagnosticManager diagnosticManager;
    private @Nullable FrontendAnalysisData analysisData;
    private @Nullable LirModule lirModule;
    private @Nullable List<FunctionLoweringContext> functionLoweringContexts;
    private boolean stopRequested;

    public FrontendLoweringContext(
            @NotNull FrontendModule module,
            @NotNull ClassRegistry classRegistry,
            @NotNull DiagnosticManager diagnosticManager
    ) {
        this.module = Objects.requireNonNull(module, "module must not be null");
        this.classRegistry = Objects.requireNonNull(classRegistry, "classRegistry must not be null");
        this.diagnosticManager = Objects.requireNonNull(diagnosticManager, "diagnosticManager must not be null");
    }

    public @NotNull FrontendModule module() {
        return module;
    }

    public @NotNull ClassRegistry classRegistry() {
        return classRegistry;
    }

    public @NotNull DiagnosticManager diagnosticManager() {
        return diagnosticManager;
    }

    public void publishAnalysisData(@NotNull FrontendAnalysisData analysisData) {
        this.analysisData = Objects.requireNonNull(analysisData, "analysisData must not be null");
    }

    public @Nullable FrontendAnalysisData analysisDataOrNull() {
        return analysisData;
    }

    public @NotNull FrontendAnalysisData requireAnalysisData() {
        if (analysisData == null) {
            throw new IllegalStateException("analysisData has not been published yet");
        }
        return analysisData;
    }

    public void publishLirModule(@NotNull LirModule lirModule) {
        this.lirModule = Objects.requireNonNull(lirModule, "lirModule must not be null");
    }

    public @Nullable LirModule lirModuleOrNull() {
        return lirModule;
    }

    public @NotNull LirModule requireLirModule() {
        if (lirModule == null) {
            throw new IllegalStateException("lirModule has not been published yet");
        }
        return lirModule;
    }

    public void publishFunctionLoweringContexts(@NotNull List<FunctionLoweringContext> functionLoweringContexts) {
        this.functionLoweringContexts = List.copyOf(Objects.requireNonNull(
                functionLoweringContexts,
                "functionLoweringContexts must not be null"
        ));
    }

    public @Nullable List<FunctionLoweringContext> functionLoweringContextsOrNull() {
        return functionLoweringContexts;
    }

    public @NotNull List<FunctionLoweringContext> requireFunctionLoweringContexts() {
        if (functionLoweringContexts == null) {
            throw new IllegalStateException("functionLoweringContexts have not been published yet");
        }
        return functionLoweringContexts;
    }

    public void requestStop() {
        stopRequested = true;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }
}
