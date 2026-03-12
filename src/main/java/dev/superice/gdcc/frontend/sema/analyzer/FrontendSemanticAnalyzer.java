package dev.superice.gdcc.frontend.sema.analyzer;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.parse.FrontendSourceUnit;
import dev.superice.gdcc.frontend.sema.FrontendAnalysisData;
import dev.superice.gdcc.frontend.sema.FrontendClassSkeletonBuilder;
import dev.superice.gdcc.scope.ClassRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.List;

/// Basic frontend semantic-analyzer framework.
///
/// The current framework already wires three stable frontend phases into one shared
/// `FrontendAnalysisData` carrier:
/// - skeleton publication
/// - lexical scope graph construction
/// - diagnostics boundary refresh after each phase
public final class FrontendSemanticAnalyzer {
    private final @NotNull FrontendClassSkeletonBuilder classSkeletonBuilder;
    private final @NotNull FrontendScopeAnalyzer scopeAnalyzer;

    public FrontendSemanticAnalyzer() {
        this(new FrontendClassSkeletonBuilder(), new FrontendScopeAnalyzer());
    }

    public FrontendSemanticAnalyzer(@NotNull FrontendClassSkeletonBuilder classSkeletonBuilder) {
        this(classSkeletonBuilder, new FrontendScopeAnalyzer());
    }

    public FrontendSemanticAnalyzer(
            @NotNull FrontendClassSkeletonBuilder classSkeletonBuilder,
            @NotNull FrontendScopeAnalyzer scopeAnalyzer
    ) {
        this.classSkeletonBuilder = Objects.requireNonNull(classSkeletonBuilder, "classSkeletonBuilder must not be null");
        this.scopeAnalyzer = Objects.requireNonNull(scopeAnalyzer, "scopeAnalyzer must not be null");
    }

    /// Runs the current frontend analyzer framework against one module using a shared
    /// `DiagnosticManager`.
    ///
    /// `FrontendSourceUnit` no longer stores parse diagnostics. The analyzer therefore consumes
    /// parse diagnostics only through the shared manager state that callers prepared earlier in
    /// the pipeline.
    public @NotNull FrontendAnalysisData analyze(
            @NotNull String moduleName,
            @NotNull List<FrontendSourceUnit> units,
            @NotNull ClassRegistry classRegistry,
            @NotNull DiagnosticManager diagnosticManager
    ) {
        Objects.requireNonNull(moduleName, "moduleName must not be null");
        Objects.requireNonNull(units, "units must not be null");
        Objects.requireNonNull(classRegistry, "classRegistry must not be null");
        Objects.requireNonNull(diagnosticManager, "diagnosticManager must not be null");

        var analysisData = FrontendAnalysisData.bootstrap();
        var moduleSkeleton = classSkeletonBuilder.build(
                moduleName,
                units,
                classRegistry,
                diagnosticManager,
                analysisData
        );

        // Publish the skeleton boundary before the scope phase starts so later phases can rely on
        // a stable module snapshot instead of peeking into builder internals.
        analysisData.updateModuleSkeleton(moduleSkeleton);
        analysisData.updateDiagnostics(diagnosticManager.snapshot());

        // Scope analysis remains a dedicated phase after skeleton publication so later binder/body
        // work can consume one stable lexical graph instead of interleaving scope creation with
        // later semantic binding.
        scopeAnalyzer.analyze(classRegistry, analysisData, diagnosticManager);
        analysisData.updateDiagnostics(diagnosticManager.snapshot());
        return analysisData;
    }
}
