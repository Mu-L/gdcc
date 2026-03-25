package dev.superice.gdcc.frontend.lowering;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.parse.FrontendModule;
import dev.superice.gdcc.frontend.sema.FrontendAnalysisData;
import dev.superice.gdcc.lir.LirModule;
import dev.superice.gdcc.scope.ClassRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    public void requestStop() {
        stopRequested = true;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }
}
