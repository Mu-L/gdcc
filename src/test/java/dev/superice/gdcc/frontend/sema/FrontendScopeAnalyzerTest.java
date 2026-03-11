package dev.superice.gdcc.frontend.sema;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.diagnostic.DiagnosticSnapshot;
import dev.superice.gdcc.frontend.parse.GdScriptParserService;
import dev.superice.gdcc.frontend.sema.analyzer.FrontendScopeAnalyzer;
import dev.superice.gdcc.frontend.sema.analyzer.FrontendSemanticAnalyzer;
import dev.superice.gdcc.gdextension.ExtensionApiLoader;
import dev.superice.gdcc.scope.ClassRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendScopeAnalyzerTest {
    @Test
    void analyzeRejectsAnalysisDataWithoutPublishedModuleSkeletonBoundary() throws Exception {
        var analyzer = new FrontendScopeAnalyzer();
        var registry = new ClassRegistry(ExtensionApiLoader.loadDefault());
        var analysisData = FrontendAnalysisData.bootstrap();
        var diagnostics = new DiagnosticManager();

        assertThrows(IllegalStateException.class, () -> analyzer.analyze(registry, analysisData, diagnostics));
    }

    @Test
    void analyzeRejectsAnalysisDataWithoutPublishedPreScopeDiagnosticsBoundary() throws Exception {
        var analyzer = new FrontendScopeAnalyzer();
        var registry = new ClassRegistry(ExtensionApiLoader.loadDefault());
        var analysisData = FrontendAnalysisData.bootstrap();
        var diagnostics = new DiagnosticManager();
        analysisData.updateModuleSkeleton(new FrontendModuleSkeleton("test_module", List.of(), List.of(), diagnostics.snapshot()));

        assertThrows(IllegalStateException.class, () -> analyzer.analyze(registry, analysisData, diagnostics));
    }

    @Test
    void analyzeRepublishesAnExplicitButCurrentlyEmptyScopeSideTable() throws Exception {
        var analyzer = new FrontendScopeAnalyzer();
        var registry = new ClassRegistry(ExtensionApiLoader.loadDefault());
        var analysisData = publishedAnalysisData();
        var originalSideTable = analysisData.scopesByAst();
        var staleAstNode = new Object();
        analysisData.scopesByAst().put(staleAstNode, registry);

        analyzer.analyze(registry, analysisData, new DiagnosticManager());

        assertSame(originalSideTable, analysisData.scopesByAst());
        assertFalse(analysisData.scopesByAst().containsKey(staleAstNode));
        assertTrue(analysisData.scopesByAst().isEmpty());
    }

    @Test
    void analyzeOnlyExposesTheManagerAwarePublicEntryPoint() {
        var analyzeMethods = List.of(FrontendScopeAnalyzer.class.getDeclaredMethods()).stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("analyze"))
                .toList();

        assertEquals(1, analyzeMethods.size());
        assertArrayEquals(
                new Class<?>[]{ClassRegistry.class, FrontendAnalysisData.class, DiagnosticManager.class},
                analyzeMethods.getFirst().getParameterTypes()
        );
    }

    @Test
    void semanticAnalyzerPublishesSkeletonBoundaryBeforeScopePhaseAndRefreshesDiagnosticsAfterIt() throws Exception {
        var parserService = new GdScriptParserService();
        var diagnostics = new DiagnosticManager();
        var unit = parserService.parseUnit(Path.of("tmp", "scope_phase_probe.gd"), """
                class_name ScopePhaseProbe
                extends Node
                
                func ping(value):
                    pass
                """, diagnostics);
        var registry = new ClassRegistry(ExtensionApiLoader.loadDefault());
        var probeScopeAnalyzer = new RecordingScopeAnalyzer();
        var analyzer = new FrontendSemanticAnalyzer(new FrontendClassSkeletonBuilder(), probeScopeAnalyzer);

        var result = analyzer.analyze("test_module", List.of(unit), registry, diagnostics);

        assertTrue(probeScopeAnalyzer.invoked);
        assertTrue(probeScopeAnalyzer.moduleSkeletonPublished);
        assertTrue(probeScopeAnalyzer.preScopeDiagnosticsMatchedManager);
        assertTrue(result.scopesByAst().isEmpty());
        assertEquals(probeScopeAnalyzer.preScopeDiagnostics, result.moduleSkeleton().diagnostics());
        assertEquals(probeScopeAnalyzer.preScopeDiagnostics.size() + 1, result.diagnostics().size());
        assertEquals("sema.scope_phase_probe", result.diagnostics().getLast().category());
        assertEquals(result.diagnostics(), diagnostics.snapshot());
    }

    private FrontendAnalysisData publishedAnalysisData() {
        var analysisData = FrontendAnalysisData.bootstrap();
        var boundaryDiagnostics = new DiagnosticSnapshot(List.of());
        analysisData.updateModuleSkeleton(new FrontendModuleSkeleton("test_module", List.of(), List.of(), boundaryDiagnostics));
        analysisData.updateDiagnostics(boundaryDiagnostics);
        return analysisData;
    }

    /// Scope-phase probe used by the integration test to anchor phase ordering.
    ///
    /// It observes what `FrontendSemanticAnalyzer` has already published when the scope phase
    /// starts, then appends a synthetic diagnostic to prove that the outer analyzer refreshes the
    /// final diagnostics snapshot after the scope phase returns.
    private static final class RecordingScopeAnalyzer extends FrontendScopeAnalyzer {
        private boolean invoked;
        private boolean moduleSkeletonPublished;
        private boolean preScopeDiagnosticsMatchedManager;
        private DiagnosticSnapshot preScopeDiagnostics;

        @Override
        public void analyze(
                ClassRegistry classRegistry,
                FrontendAnalysisData analysisData,
                DiagnosticManager diagnosticManager
        ) {
            invoked = true;
            moduleSkeletonPublished = analysisData.moduleSkeleton().classDefs().size() == 1;
            preScopeDiagnostics = analysisData.diagnostics();
            preScopeDiagnosticsMatchedManager = preScopeDiagnostics.equals(diagnosticManager.snapshot());
            diagnosticManager.warning(
                    "sema.scope_phase_probe",
                    "scope phase probe diagnostic",
                    null,
                    null
            );
            super.analyze(classRegistry, analysisData, diagnosticManager);
        }
    }
}
