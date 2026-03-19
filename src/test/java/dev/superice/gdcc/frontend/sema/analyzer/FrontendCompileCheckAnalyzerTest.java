package dev.superice.gdcc.frontend.sema.analyzer;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.diagnostic.DiagnosticSnapshot;
import dev.superice.gdcc.frontend.diagnostic.FrontendDiagnostic;
import dev.superice.gdcc.frontend.diagnostic.FrontendDiagnosticSeverity;
import dev.superice.gdcc.frontend.diagnostic.FrontendRange;
import dev.superice.gdcc.frontend.parse.FrontendSourceUnit;
import dev.superice.gdcc.frontend.parse.GdScriptParserService;
import dev.superice.gdcc.frontend.sema.FrontendAnalysisData;
import dev.superice.gdcc.frontend.sema.FrontendClassSkeletonBuilder;
import dev.superice.gdcc.gdextension.ExtensionApiLoader;
import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdparser.frontend.ast.ArrayExpression;
import dev.superice.gdparser.frontend.ast.Expression;
import dev.superice.gdparser.frontend.ast.FunctionDeclaration;
import dev.superice.gdparser.frontend.ast.Node;
import dev.superice.gdparser.frontend.ast.VariableDeclaration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendCompileCheckAnalyzerTest {
    @Test
    void analyzeRejectsMissingModuleSkeletonBoundary() throws Exception {
        var analyzer = new FrontendCompileCheckAnalyzer();
        var analysisData = FrontendAnalysisData.bootstrap();

        var thrown = assertThrows(
                IllegalStateException.class,
                () -> analyzer.analyze(analysisData, new DiagnosticManager())
        );

        assertTrue(thrown.getMessage().contains("moduleSkeleton"));
    }

    @Test
    void analyzeRejectsMissingDiagnosticsBoundary() throws Exception {
        var preparedInput = prepareCompileCheckInput("missing_compile_check_diagnostics.gd", """
                class_name MissingCompileCheckDiagnostics
                extends Node
                
                func ping():
                    pass
                """);
        var analysisData = FrontendAnalysisData.bootstrap();
        analysisData.updateModuleSkeleton(preparedInput.analysisData().moduleSkeleton());

        var thrown = assertThrows(
                IllegalStateException.class,
                () -> new FrontendCompileCheckAnalyzer().analyze(analysisData, preparedInput.diagnosticManager())
        );

        assertTrue(thrown.getMessage().contains("diagnostics"));
    }

    @Test
    void analyzeForCompileReportsExplicitCompileBlocksWhileAnalyzeLeavesSharedDiagnosticsUntouched() throws Exception {
        var source = """
                class_name CompileCheckExplicitBlocks
                extends Node
                
                var property_array = [1]
                var property_preload = preload("res://icon.svg")
                
                func ping(value):
                    assert(value, "compile-only gate")
                    {"hp": 1}
                    $Camera3D
                    value as String
                    value is String
                """;

        var sharedAnalyzed = analyzeShared("compile_check_explicit_blocks.gd", source);
        assertTrue(diagnosticsByCategory(sharedAnalyzed.diagnostics(), "sema.compile_check").isEmpty());

        var compiled = analyzeForCompile("compile_check_explicit_blocks.gd", source);
        var compileDiagnostics = diagnosticsByCategory(compiled.diagnostics(), "sema.compile_check");
        assertEquals(7, compileDiagnostics.size());
        assertTrue(compileDiagnostics.stream().allMatch(diagnostic ->
                diagnostic.severity() == FrontendDiagnosticSeverity.ERROR
                        && Path.of("tmp", "compile_check_explicit_blocks.gd").equals(diagnostic.sourcePath())
                        && diagnostic.range() != null
        ));
        assertTrue(compileDiagnostics.stream().anyMatch(diagnostic -> diagnostic.message().contains("assert statement")));
        assertTrue(compileDiagnostics.stream().anyMatch(diagnostic -> diagnostic.message().contains("Array literal")));
        assertTrue(compileDiagnostics.stream().anyMatch(diagnostic -> diagnostic.message().contains("Dictionary literal")));
        assertTrue(compileDiagnostics.stream().anyMatch(diagnostic -> diagnostic.message().contains("Preload expression")));
        assertTrue(compileDiagnostics.stream().anyMatch(diagnostic -> diagnostic.message().contains("Get-node expression")));
        assertTrue(compileDiagnostics.stream().anyMatch(diagnostic -> diagnostic.message().contains("Cast expression")));
        assertTrue(compileDiagnostics.stream().anyMatch(diagnostic -> diagnostic.message().contains("Type-test expression")));
        assertEquals(compiled.diagnostics(), compiled.diagnosticManager().snapshot());
    }

    @Test
    void analyzeForCompileSkipsExplicitCompileBlocksOutsideCompileSurface() throws Exception {
        var source = """
                class_name CompileCheckSkippedSurface
                extends Node
                
                func helper():
                    pass
                
                func ping(seed = [1]):
                    var body_local = 0
                    var f = func():
                        {"hp": body_local}
                        preload("res://icon.svg")
                        $Camera3D
                        body_local as int
                        body_local is int
                        assert(body_local)
                    const answer = [body_local]
                    for item in [body_local]:
                        {"item": item}
                        preload("res://icon.svg")
                        $Camera3D
                        item as int
                        item is int
                        assert(item)
                    match body_local:
                        var bound when bound > 0:
                            [bound]
                            preload("res://icon.svg")
                            $Camera3D
                            bound as int
                            bound is int
                            assert(bound)
                    return body_local
                """;

        var compiled = analyzeForCompile("compile_check_skipped_surface.gd", source);

        assertTrue(diagnosticsByCategory(compiled.diagnostics(), "sema.compile_check").isEmpty());
        var unsupportedBindingDiagnostics = diagnosticsByCategory(
                compiled.diagnostics(),
                "sema.unsupported_binding_subtree"
        );
        assertEquals(5, unsupportedBindingDiagnostics.size());
    }

    @Test
    void analyzeSkipsCompileCheckWhenAnchorAlreadyHasPublishedError() throws Exception {
        var preparedInput = prepareCompileCheckInput("compile_check_existing_error.gd", """
                class_name CompileCheckExistingError
                extends Node
                
                func ping():
                    [1]
                """);
        var arrayExpression = findNode(preparedInput.unit().ast(), ArrayExpression.class, ignored -> true);
        preparedInput.diagnosticManager().error(
                "sema.synthetic",
                "synthetic upstream error",
                preparedInput.unit().path(),
                FrontendRange.fromAstRange(arrayExpression.range())
        );
        preparedInput.analysisData().updateDiagnostics(preparedInput.diagnosticManager().snapshot());

        new FrontendCompileCheckAnalyzer().analyze(
                preparedInput.analysisData(),
                preparedInput.diagnosticManager()
        );

        assertTrue(diagnosticsByCategory(preparedInput.diagnosticManager().snapshot(), "sema.compile_check").isEmpty());
        assertEquals(1, diagnosticsByCategory(preparedInput.diagnosticManager().snapshot(), "sema.synthetic").size());
    }

    private static @NotNull AnalyzedScript analyzeShared(
            @NotNull String fileName,
            @NotNull String source
    ) throws Exception {
        var parserService = new GdScriptParserService();
        var parseDiagnostics = new DiagnosticManager();
        var unit = parserService.parseUnit(Path.of("tmp", fileName), source, parseDiagnostics);
        assertTrue(parseDiagnostics.isEmpty(), () -> "Unexpected parse diagnostics: " + parseDiagnostics.snapshot());

        var diagnosticManager = new DiagnosticManager();
        var analysisData = new FrontendSemanticAnalyzer().analyze(
                "test_module",
                List.of(unit),
                new ClassRegistry(ExtensionApiLoader.loadDefault()),
                diagnosticManager
        );
        return new AnalyzedScript(unit, analysisData.diagnostics(), diagnosticManager);
    }

    private static @NotNull AnalyzedScript analyzeForCompile(
            @NotNull String fileName,
            @NotNull String source
    ) throws Exception {
        var parserService = new GdScriptParserService();
        var parseDiagnostics = new DiagnosticManager();
        var unit = parserService.parseUnit(Path.of("tmp", fileName), source, parseDiagnostics);
        assertTrue(parseDiagnostics.isEmpty(), () -> "Unexpected parse diagnostics: " + parseDiagnostics.snapshot());

        var diagnosticManager = new DiagnosticManager();
        var analysisData = new FrontendSemanticAnalyzer().analyzeForCompile(
                "test_module",
                List.of(unit),
                new ClassRegistry(ExtensionApiLoader.loadDefault()),
                diagnosticManager
        );
        return new AnalyzedScript(unit, analysisData.diagnostics(), diagnosticManager);
    }

    private static @NotNull PreparedCompileCheckInput prepareCompileCheckInput(
            @NotNull String fileName,
            @NotNull String source
    ) throws Exception {
        var parserService = new GdScriptParserService();
        var diagnosticManager = new DiagnosticManager();
        var unit = parserService.parseUnit(Path.of("tmp", fileName), source, diagnosticManager);
        assertTrue(diagnosticManager.isEmpty(), () -> "Unexpected parse diagnostics: " + diagnosticManager.snapshot());

        var classRegistry = new ClassRegistry(ExtensionApiLoader.loadDefault());
        var analysisData = FrontendAnalysisData.bootstrap();
        var moduleSkeleton = new FrontendClassSkeletonBuilder().build(
                "test_module",
                List.of(unit),
                classRegistry,
                diagnosticManager,
                analysisData
        );
        analysisData.updateModuleSkeleton(moduleSkeleton);
        analysisData.updateDiagnostics(diagnosticManager.snapshot());
        new FrontendScopeAnalyzer().analyze(classRegistry, analysisData, diagnosticManager);
        analysisData.updateDiagnostics(diagnosticManager.snapshot());
        new FrontendVariableAnalyzer().analyze(analysisData, diagnosticManager);
        analysisData.updateDiagnostics(diagnosticManager.snapshot());
        new FrontendTopBindingAnalyzer().analyze(analysisData, diagnosticManager);
        analysisData.updateDiagnostics(diagnosticManager.snapshot());
        new FrontendChainBindingAnalyzer().analyze(classRegistry, analysisData, diagnosticManager);
        analysisData.updateDiagnostics(diagnosticManager.snapshot());
        new FrontendExprTypeAnalyzer().analyze(classRegistry, analysisData, diagnosticManager);
        analysisData.updateDiagnostics(diagnosticManager.snapshot());
        new FrontendAnnotationUsageAnalyzer().analyze(classRegistry, analysisData, diagnosticManager);
        analysisData.updateDiagnostics(diagnosticManager.snapshot());
        new FrontendTypeCheckAnalyzer().analyze(classRegistry, analysisData, diagnosticManager);
        analysisData.updateDiagnostics(diagnosticManager.snapshot());
        return new PreparedCompileCheckInput(unit, analysisData, diagnosticManager);
    }

    private static @NotNull List<FrontendDiagnostic> diagnosticsByCategory(
            @NotNull DiagnosticSnapshot diagnostics,
            @NotNull String category
    ) {
        return diagnostics.asList().stream()
                .filter(diagnostic -> diagnostic.category().equals(category))
                .toList();
    }

    private static <T extends Node> @NotNull T findNode(
            @NotNull Node root,
            @NotNull Class<T> nodeType,
            @NotNull Predicate<T> predicate
    ) {
        if (nodeType.isInstance(root)) {
            var candidate = nodeType.cast(root);
            if (predicate.test(candidate)) {
                return candidate;
            }
        }
        for (var child : root.getChildren()) {
            try {
                return findNode(child, nodeType, predicate);
            } catch (AssertionError ignored) {
            }
        }
        throw new AssertionError("Node not found: " + nodeType.getSimpleName());
    }

    private record AnalyzedScript(
            @NotNull FrontendSourceUnit unit,
            @NotNull DiagnosticSnapshot diagnostics,
            @NotNull DiagnosticManager diagnosticManager
    ) {
        private AnalyzedScript {
            Objects.requireNonNull(unit, "unit must not be null");
            Objects.requireNonNull(diagnostics, "diagnostics must not be null");
            Objects.requireNonNull(diagnosticManager, "diagnosticManager must not be null");
        }
    }

    private record PreparedCompileCheckInput(
            @NotNull FrontendSourceUnit unit,
            @NotNull FrontendAnalysisData analysisData,
            @NotNull DiagnosticManager diagnosticManager
    ) {
        private PreparedCompileCheckInput {
            Objects.requireNonNull(unit, "unit must not be null");
            Objects.requireNonNull(analysisData, "analysisData must not be null");
            Objects.requireNonNull(diagnosticManager, "diagnosticManager must not be null");
        }
    }
}
