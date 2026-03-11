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
/// This round intentionally stops at phase wiring: it builds the existing skeleton result,
/// inserts a dedicated scope phase boundary, and returns one shared `FrontendAnalysisData`
/// carrier that already owns every planned side table. Binder, lexical scope graph population,
/// expression typing, and member/call resolution remain future work.
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

        // Scope analysis is a separate phase even before it starts producing real scope facts.
        // This keeps the pipeline shape aligned with the implementation plan and with Godot's
        // staged analyzer flow.
        scopeAnalyzer.analyze(classRegistry, analysisData, diagnosticManager);
        analysisData.updateDiagnostics(diagnosticManager.snapshot());
        return analysisData;
    }
}
