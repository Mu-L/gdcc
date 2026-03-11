package dev.superice.gdcc.frontend.sema.analyzer;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.sema.FrontendAnalysisData;
import dev.superice.gdcc.frontend.sema.FrontendAstSideTable;
import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdcc.scope.Scope;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Scope-phase worker that sits between skeleton publication and future binder/body passes.
///
/// Phase 1 intentionally keeps this analyzer narrow:
/// - it establishes an explicit scope phase boundary in the semantic pipeline
/// - it requires skeleton outputs and the pre-scope diagnostics snapshot to already be published
/// - it republishes the shared `scopesByAst` side table through `FrontendAnalysisData`
///
/// What it intentionally does **not** do yet:
/// - walk the AST
/// - create `ClassScope` / `CallableScope` / `BlockScope`
/// - prefill parameters or any other bindings
///
/// That work starts in Phase 2. Keeping this class separate from `frontend.scope` preserves the
/// layering boundary between protocol objects and semantic-phase orchestration.
public class FrontendScopeAnalyzer {
    /// Runs the scope phase against the shared analysis carrier.
    ///
    /// The current implementation is deliberately framework-only. It validates that the previous
    /// skeleton phase has already published both:
    /// - `moduleSkeleton()`
    /// - the diagnostics snapshot captured right after skeleton
    ///
    /// Then it republishes an explicit, currently empty scope side table so the pipeline has a
    /// stable scope-phase handoff point before Phase 2 starts filling lexical scope facts.
    public void analyze(
            @NotNull ClassRegistry classRegistry,
            @NotNull FrontendAnalysisData analysisData,
            @NotNull DiagnosticManager diagnosticManager
    ) {
        Objects.requireNonNull(classRegistry, "classRegistry must not be null");
        Objects.requireNonNull(analysisData, "analysisData must not be null");
        Objects.requireNonNull(diagnosticManager, "diagnosticManager must not be null");

        // Phase ordering matters: scope analysis is defined to start only after skeleton facts and
        // the corresponding boundary snapshot have both become observable to later phases.
        analysisData.moduleSkeleton();
        analysisData.diagnostics();

        var scopesByAst = new FrontendAstSideTable<Scope>();
        analysisData.updateScopesByAst(scopesByAst);
    }
}
