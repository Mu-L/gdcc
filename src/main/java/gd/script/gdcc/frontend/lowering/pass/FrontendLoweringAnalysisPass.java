package gd.script.gdcc.frontend.lowering.pass;

import gd.script.gdcc.frontend.lowering.FrontendLoweringContext;
import gd.script.gdcc.frontend.lowering.FrontendLoweringPass;
import gd.script.gdcc.frontend.sema.analyzer.FrontendSemanticAnalyzer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Lowering pass that establishes the compile-ready frontend analysis boundary.
///
/// Lowering is only allowed to continue from `analyzeForCompile(...)` output. If the shared
/// diagnostics already contain compile-surface errors, the pipeline stops before any LIR emission.
public final class FrontendLoweringAnalysisPass implements FrontendLoweringPass {
    private final @NotNull FrontendSemanticAnalyzer semanticAnalyzer;

    public FrontendLoweringAnalysisPass() {
        this(new FrontendSemanticAnalyzer());
    }

    public FrontendLoweringAnalysisPass(@NotNull FrontendSemanticAnalyzer semanticAnalyzer) {
        this.semanticAnalyzer = Objects.requireNonNull(semanticAnalyzer, "semanticAnalyzer must not be null");
    }

    @Override
    public void run(@NotNull FrontendLoweringContext context) {
        var analysisData = semanticAnalyzer.analyzeForCompile(
                context.module(),
                context.classRegistry(),
                context.diagnosticManager()
        );
        context.publishAnalysisData(analysisData);
        if (analysisData.diagnostics().hasErrors()) {
            context.requestStop();
        }
    }
}
