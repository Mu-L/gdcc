package dev.superice.gdcc.frontend.sema;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.diagnostic.DiagnosticSnapshot;
import dev.superice.gdcc.frontend.diagnostic.FrontendDiagnostic;
import dev.superice.gdcc.frontend.diagnostic.FrontendDiagnosticSeverity;
import dev.superice.gdcc.frontend.diagnostic.FrontendRange;
import dev.superice.gdcc.frontend.parse.FrontendSourceUnit;
import dev.superice.gdcc.frontend.parse.GdScriptParserService;
import dev.superice.gdcc.frontend.scope.BlockScope;
import dev.superice.gdcc.frontend.scope.CallableScope;
import dev.superice.gdcc.frontend.sema.analyzer.FrontendScopeAnalyzer;
import dev.superice.gdcc.frontend.sema.analyzer.FrontendVariableAnalyzer;
import dev.superice.gdcc.gdextension.ExtensionApiLoader;
import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdcc.scope.ScopeValueKind;
import dev.superice.gdcc.type.GdIntType;
import dev.superice.gdcc.type.GdVariantType;
import dev.superice.gdparser.frontend.ast.ClassDeclaration;
import dev.superice.gdparser.frontend.ast.ConstructorDeclaration;
import dev.superice.gdparser.frontend.ast.FunctionDeclaration;
import dev.superice.gdparser.frontend.ast.LambdaExpression;
import dev.superice.gdparser.frontend.ast.Statement;
import dev.superice.gdparser.frontend.ast.VariableDeclaration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendVariableAnalyzerTest {
    @Test
    void analyzeRejectsAnalysisDataWithoutPublishedModuleSkeletonBoundary() {
        var analyzer = new FrontendVariableAnalyzer();
        var diagnostics = new DiagnosticManager();

        assertThrows(
                IllegalStateException.class,
                () -> analyzer.analyze(FrontendAnalysisData.bootstrap(), diagnostics)
        );
    }

    @Test
    void analyzeRejectsAnalysisDataWithoutPublishedPreVariableDiagnosticsBoundary() {
        var analyzer = new FrontendVariableAnalyzer();
        var analysisData = FrontendAnalysisData.bootstrap();
        var diagnostics = new DiagnosticManager();
        analysisData.updateModuleSkeleton(new FrontendModuleSkeleton("test_module", List.of(), diagnostics.snapshot()));

        assertThrows(IllegalStateException.class, () -> analyzer.analyze(analysisData, diagnostics));
    }

    @Test
    void analyzeRejectsAcceptedSourcesBeforeScopePhasePublishesTopLevelScopes() throws Exception {
        var parserService = new GdScriptParserService();
        var diagnostics = new DiagnosticManager();
        var unit = parserService.parseUnit(Path.of("tmp", "missing_scope_boundary.gd"), """
                class_name MissingScopeBoundary
                extends Node
                
                func ping(value):
                    pass
                """, diagnostics);
        assertTrue(diagnostics.isEmpty(), () -> "Unexpected parse diagnostics: " + diagnostics.snapshot());

        var analysisData = FrontendAnalysisData.bootstrap();
        var moduleSkeleton = new FrontendClassSkeletonBuilder().build(
                "test_module",
                List.of(unit),
                newRegistry(),
                diagnostics,
                analysisData
        );
        analysisData.updateModuleSkeleton(moduleSkeleton);
        analysisData.updateDiagnostics(diagnostics.snapshot());

        var error = assertThrows(
                IllegalStateException.class,
                () -> new FrontendVariableAnalyzer().analyze(analysisData, diagnostics)
        );
        assertTrue(error.getMessage().contains("Scope graph has not been published"));
        assertTrue(error.getMessage().contains(unit.path().toString()));
    }

    @Test
    void analyzeBindsFunctionAndConstructorParametersWhileKeepingLocalsDeferred() throws Exception {
        var phaseInput = publishedPhaseInput("phase3_parameter_prefill.gd", """
                class_name VariablePhaseBoundary
                extends Node

                func ping(value: int, alias):
                    var local := value
                    return alias

                func _init(seed: int, mirror):
                    pass
                """);
        var analysisData = phaseInput.analysisData();
        var sourceFile = phaseInput.unit().ast();
        var scopesByAst = analysisData.scopesByAst();
        var pingFunction = findStatement(
                sourceFile.statements(),
                FunctionDeclaration.class,
                functionDeclaration -> functionDeclaration.name().equals("ping")
        );
        var constructor = findStatement(sourceFile.statements(), ConstructorDeclaration.class, _ -> true);
        var pingScope = assertInstanceOf(CallableScope.class, scopesByAst.get(pingFunction));
        var pingBodyScope = assertInstanceOf(BlockScope.class, scopesByAst.get(pingFunction.body()));
        var constructorScope = assertInstanceOf(CallableScope.class, scopesByAst.get(constructor));
        var localDeclaration = findStatement(
                pingFunction.body().statements(),
                VariableDeclaration.class,
                variableDeclaration -> variableDeclaration.name().equals("local")
        );

        new FrontendVariableAnalyzer().analyze(analysisData, phaseInput.diagnostics());

        var valueBinding = pingScope.resolveValue("value");
        assertNotNull(valueBinding);
        assertEquals(GdIntType.INT, valueBinding.type());
        assertEquals(ScopeValueKind.PARAMETER, valueBinding.kind());
        assertSame(pingFunction.parameters().getFirst(), valueBinding.declaration());

        var aliasBinding = pingScope.resolveValue("alias");
        assertNotNull(aliasBinding);
        assertEquals(GdVariantType.VARIANT, aliasBinding.type());
        assertEquals(ScopeValueKind.PARAMETER, aliasBinding.kind());
        assertSame(pingFunction.parameters().getLast(), aliasBinding.declaration());

        var seedBinding = constructorScope.resolveValue("seed");
        assertNotNull(seedBinding);
        assertEquals(GdIntType.INT, seedBinding.type());
        assertEquals(ScopeValueKind.PARAMETER, seedBinding.kind());

        var mirrorBinding = constructorScope.resolveValue("mirror");
        assertNotNull(mirrorBinding);
        assertEquals(GdVariantType.VARIANT, mirrorBinding.type());
        assertEquals(ScopeValueKind.PARAMETER, mirrorBinding.kind());

        assertNull(pingBodyScope.resolveValue("local"));
        assertSame(scopesByAst, analysisData.scopesByAst());
        assertSame(pingScope, analysisData.scopesByAst().get(pingFunction));
        assertSame(pingBodyScope, analysisData.scopesByAst().get(pingFunction.body()));
        assertSame(pingBodyScope, analysisData.scopesByAst().get(localDeclaration));
        assertTrue(analysisData.symbolBindings().isEmpty());
        assertTrue(analysisData.expressionTypes().isEmpty());
        assertTrue(analysisData.resolvedMembers().isEmpty());
        assertTrue(analysisData.resolvedCalls().isEmpty());
        assertTrue(phaseInput.diagnostics().isEmpty());
        assertEquals(phaseInput.diagnostics().snapshot(), analysisData.diagnostics());
    }

    @Test
    void analyzeWarnsForDefaultValuesButStillBindsParameters() throws Exception {
        var phaseInput = publishedPhaseInput("phase3_parameter_default.gd", """
                class_name ParameterDefaultWarning
                extends Node

                func ping(value, alias = value):
                    pass
                """);
        var sourceFile = phaseInput.unit().ast();
        var pingFunction = findStatement(
                sourceFile.statements(),
                FunctionDeclaration.class,
                functionDeclaration -> functionDeclaration.name().equals("ping")
        );
        var pingScope = assertInstanceOf(CallableScope.class, phaseInput.analysisData().scopesByAst().get(pingFunction));
        var diagnosticsBefore = phaseInput.diagnostics().snapshot();

        new FrontendVariableAnalyzer().analyze(phaseInput.analysisData(), phaseInput.diagnostics());

        var diagnosticsAfter = phaseInput.diagnostics().snapshot();
        var newDiagnostics = newDiagnostics(diagnosticsBefore, diagnosticsAfter);
        var warning = newDiagnostics.getFirst();

        assertEquals(1, newDiagnostics.size());
        assertEquals(FrontendDiagnosticSeverity.WARNING, warning.severity());
        assertEquals("sema.unsupported_parameter_default_value", warning.category());
        assertTrue(warning.message().contains("FrontendExprTypeAnalyzer"));
        assertTrue(warning.message().contains("ignores the default value expression"));
        assertEquals(phaseInput.unit().path(), warning.sourcePath());
        assertEquals(
                FrontendRange.fromAstRange(pingFunction.parameters().getLast().defaultValue().range()),
                warning.range()
        );

        var valueBinding = pingScope.resolveValue("value");
        assertNotNull(valueBinding);
        assertEquals(GdVariantType.VARIANT, valueBinding.type());
        var aliasBinding = pingScope.resolveValue("alias");
        assertNotNull(aliasBinding);
        assertEquals(GdVariantType.VARIANT, aliasBinding.type());
    }

    @Test
    void analyzeKeepsLambdaParametersDeferred() throws Exception {
        var phaseInput = publishedPhaseInput("phase3_lambda_deferred.gd", """
                class_name LambdaParameterDeferred
                extends Node

                func ping(seed: int):
                    var builder := func(item: int, fallback = item):
                        return fallback
                    return seed
                """);
        var sourceFile = phaseInput.unit().ast();
        var pingFunction = findStatement(
                sourceFile.statements(),
                FunctionDeclaration.class,
                functionDeclaration -> functionDeclaration.name().equals("ping")
        );
        var builderDeclaration = findStatement(
                pingFunction.body().statements(),
                VariableDeclaration.class,
                variableDeclaration -> variableDeclaration.name().equals("builder")
        );
        var builderLambda = assertInstanceOf(LambdaExpression.class, builderDeclaration.value());
        var lambdaScope = assertInstanceOf(CallableScope.class, phaseInput.analysisData().scopesByAst().get(builderLambda));
        var diagnosticsBefore = phaseInput.diagnostics().snapshot();

        new FrontendVariableAnalyzer().analyze(phaseInput.analysisData(), phaseInput.diagnostics());

        assertNotNull(assertInstanceOf(
                CallableScope.class,
                phaseInput.analysisData().scopesByAst().get(pingFunction)
        ).resolveValue("seed"));
        assertNull(lambdaScope.resolveValue("item"));
        assertNull(lambdaScope.resolveValue("fallback"));
        assertEquals(diagnosticsBefore, phaseInput.diagnostics().snapshot());
    }

    @Test
    void analyzeWarnsAndFallsBackForUnknownParameterTypes() throws Exception {
        var phaseInput = publishedPhaseInput("phase3_unknown_parameter_type.gd", """
                class_name UnknownParameterType
                extends Node

                func ping(value: MissingType):
                    pass
                """);
        var sourceFile = phaseInput.unit().ast();
        var pingFunction = findStatement(
                sourceFile.statements(),
                FunctionDeclaration.class,
                functionDeclaration -> functionDeclaration.name().equals("ping")
        );
        var pingScope = assertInstanceOf(CallableScope.class, phaseInput.analysisData().scopesByAst().get(pingFunction));
        var diagnosticsBefore = phaseInput.diagnostics().snapshot();

        new FrontendVariableAnalyzer().analyze(phaseInput.analysisData(), phaseInput.diagnostics());

        var diagnosticsAfter = phaseInput.diagnostics().snapshot();
        var newDiagnostics = newDiagnostics(diagnosticsBefore, diagnosticsAfter);
        var warning = newDiagnostics.getFirst();

        assertEquals(1, newDiagnostics.size());
        assertEquals(FrontendDiagnosticSeverity.WARNING, warning.severity());
        assertEquals("sema.type_resolution", warning.category());
        assertTrue(warning.message().contains("MissingType"));
        assertEquals(phaseInput.unit().path(), warning.sourcePath());
        assertEquals(
                FrontendRange.fromAstRange(pingFunction.parameters().getFirst().type().range()),
                warning.range()
        );
        var valueBinding = pingScope.resolveValue("value");
        assertNotNull(valueBinding);
        assertEquals(GdVariantType.VARIANT, valueBinding.type());
    }

    @Test
    void analyzeReportsDuplicateParametersWithoutOverwritingFirstBinding() throws Exception {
        var phaseInput = publishedPhaseInput("phase3_duplicate_parameter.gd", """
                class_name DuplicateParameterBinding
                extends Node

                func ping(value: int, value):
                    pass
                """);
        var sourceFile = phaseInput.unit().ast();
        var pingFunction = findStatement(
                sourceFile.statements(),
                FunctionDeclaration.class,
                functionDeclaration -> functionDeclaration.name().equals("ping")
        );
        var pingScope = assertInstanceOf(CallableScope.class, phaseInput.analysisData().scopesByAst().get(pingFunction));
        var diagnosticsBefore = phaseInput.diagnostics().snapshot();

        new FrontendVariableAnalyzer().analyze(phaseInput.analysisData(), phaseInput.diagnostics());

        var diagnosticsAfter = phaseInput.diagnostics().snapshot();
        var newDiagnostics = newDiagnostics(diagnosticsBefore, diagnosticsAfter);
        var error = newDiagnostics.getFirst();
        var binding = pingScope.resolveValue("value");
        assertNotNull(binding);

        assertEquals(1, newDiagnostics.size());
        assertEquals(FrontendDiagnosticSeverity.ERROR, error.severity());
        assertEquals("sema.variable_binding", error.category());
        assertTrue(error.message().contains("Duplicate parameter 'value'"));
        assertEquals(GdIntType.INT, binding.type());
        assertSame(pingFunction.parameters().getFirst(), binding.declaration());
    }

    @Test
    void analyzeSkipsParameterWithoutScopeRecord() throws Exception {
        var phaseInput = publishedPhaseInput("phase3_missing_parameter_scope.gd", """
                class_name MissingParameterScope
                extends Node

                func ping(value: int, alias: int):
                    pass
                """);
        var sourceFile = phaseInput.unit().ast();
        var pingFunction = findStatement(
                sourceFile.statements(),
                FunctionDeclaration.class,
                functionDeclaration -> functionDeclaration.name().equals("ping")
        );
        var aliasParameter = pingFunction.parameters().getLast();
        var pingScope = assertInstanceOf(CallableScope.class, phaseInput.analysisData().scopesByAst().get(pingFunction));
        phaseInput.analysisData().scopesByAst().remove(aliasParameter);
        var diagnosticsBefore = phaseInput.diagnostics().snapshot();

        new FrontendVariableAnalyzer().analyze(phaseInput.analysisData(), phaseInput.diagnostics());

        assertEquals(diagnosticsBefore, phaseInput.diagnostics().snapshot());
        assertNotNull(pingScope.resolveValue("value"));
        assertNull(pingScope.resolveValue("alias"));
    }

    @Test
    void analyzeReportsCallableScopeMismatchAndContinuesOtherParameters() throws Exception {
        var phaseInput = publishedPhaseInput("phase3_parameter_scope_mismatch.gd", """
                class_name ParameterScopeMismatch
                extends Node

                func ping(value: int, alias: int):
                    pass
                """);
        var sourceFile = phaseInput.unit().ast();
        var pingFunction = findStatement(
                sourceFile.statements(),
                FunctionDeclaration.class,
                functionDeclaration -> functionDeclaration.name().equals("ping")
        );
        var aliasParameter = pingFunction.parameters().getLast();
        var pingScope = assertInstanceOf(CallableScope.class, phaseInput.analysisData().scopesByAst().get(pingFunction));
        var bodyScope = assertInstanceOf(BlockScope.class, phaseInput.analysisData().scopesByAst().get(pingFunction.body()));
        phaseInput.analysisData().scopesByAst().put(aliasParameter, bodyScope);
        var diagnosticsBefore = phaseInput.diagnostics().snapshot();

        new FrontendVariableAnalyzer().analyze(phaseInput.analysisData(), phaseInput.diagnostics());

        var diagnosticsAfter = phaseInput.diagnostics().snapshot();
        var newDiagnostics = newDiagnostics(diagnosticsBefore, diagnosticsAfter);
        var error = newDiagnostics.getFirst();

        assertEquals(1, newDiagnostics.size());
        assertEquals(FrontendDiagnosticSeverity.ERROR, error.severity());
        assertEquals("sema.variable_binding", error.category());
        assertTrue(error.message().contains("expected CallableScope"));
        assertTrue(error.message().contains("BlockScope"));
        assertNotNull(pingScope.resolveValue("value"));
        assertNull(pingScope.resolveValue("alias"));
    }

    @Test
    void analyzeSkipsBadInnerClassSubtreeButKeepsSiblingCallableAlive() throws Exception {
        var parserService = new GdScriptParserService();
        var diagnostics = new DiagnosticManager();
        var unit = parserService.parseUnit(Path.of("tmp", "phase3_skipped_inner_class.gd"), """
                class_name SkippedInnerClass
                extends Node

                class Broken:
                    func lost(arg: int):
                        pass

                func good(value: int):
                    pass
                """, diagnostics);
        assertTrue(diagnostics.isEmpty(), () -> "Unexpected parse diagnostics: " + diagnostics.snapshot());

        var analysisData = FrontendAnalysisData.bootstrap();
        var boundaryDiagnostics = diagnostics.snapshot();
        analysisData.updateModuleSkeleton(new FrontendModuleSkeleton(
                "test_module",
                List.of(new FrontendSourceClassRelation(
                        unit,
                        "SkippedInnerClass",
                        new FrontendSuperClassRef("Node", "Node"),
                        new LirClassDef("SkippedInnerClass", "Node"),
                        List.of()
                )),
                boundaryDiagnostics
        ));
        analysisData.updateDiagnostics(boundaryDiagnostics);
        new FrontendScopeAnalyzer().analyze(newRegistry(), analysisData, diagnostics);
        analysisData.updateDiagnostics(diagnostics.snapshot());

        var sourceFile = unit.ast();
        var brokenClass = findStatement(
                sourceFile.statements(),
                ClassDeclaration.class,
                classDeclaration -> classDeclaration.name().equals("Broken")
        );
        var lostFunction = findStatement(
                brokenClass.body().statements(),
                FunctionDeclaration.class,
                functionDeclaration -> functionDeclaration.name().equals("lost")
        );
        var goodFunction = findStatement(
                sourceFile.statements(),
                FunctionDeclaration.class,
                functionDeclaration -> functionDeclaration.name().equals("good")
        );
        var goodScope = assertInstanceOf(CallableScope.class, analysisData.scopesByAst().get(goodFunction));

        new FrontendVariableAnalyzer().analyze(analysisData, diagnostics);

        assertFalse(analysisData.scopesByAst().containsKey(brokenClass));
        assertFalse(analysisData.scopesByAst().containsKey(lostFunction));
        assertEquals(boundaryDiagnostics, diagnostics.snapshot());
        assertNotNull(goodScope.resolveValue("value"));
    }

    @Test
    void analyzeOnlyExposesManagerAwarePublicEntryPoint() {
        var analyzeMethods = Arrays.stream(FrontendVariableAnalyzer.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("analyze"))
                .toList();

        assertEquals(1, analyzeMethods.size());
        assertArrayEquals(
                new Class<?>[]{FrontendAnalysisData.class, DiagnosticManager.class},
                analyzeMethods.getFirst().getParameterTypes()
        );
    }

    private PhaseInput publishedPhaseInput(@NotNull String fileName, @NotNull String source) throws Exception {
        var parserService = new GdScriptParserService();
        var diagnostics = new DiagnosticManager();
        var unit = parserService.parseUnit(Path.of("tmp", fileName), source, diagnostics);
        assertTrue(diagnostics.isEmpty(), () -> "Unexpected parse diagnostics: " + diagnostics.snapshot());

        var registry = newRegistry();
        var analysisData = FrontendAnalysisData.bootstrap();
        var moduleSkeleton = new FrontendClassSkeletonBuilder().build(
                "test_module",
                List.of(unit),
                registry,
                diagnostics,
                analysisData
        );
        analysisData.updateModuleSkeleton(moduleSkeleton);
        analysisData.updateDiagnostics(diagnostics.snapshot());
        new FrontendScopeAnalyzer().analyze(registry, analysisData, diagnostics);
        analysisData.updateDiagnostics(diagnostics.snapshot());
        return new PhaseInput(unit, analysisData, diagnostics);
    }

    private static @NotNull List<FrontendDiagnostic> newDiagnostics(
            @NotNull DiagnosticSnapshot before,
            @NotNull DiagnosticSnapshot after
    ) {
        return after.asList().subList(before.size(), after.size());
    }

    private <T extends Statement> T findStatement(
            @NotNull List<Statement> statements,
            @NotNull Class<T> statementType,
            @NotNull Predicate<T> predicate
    ) {
        return statements.stream()
                .filter(statementType::isInstance)
                .map(statementType::cast)
                .filter(predicate)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Statement not found: " + statementType.getSimpleName()));
    }

    private static @NotNull ClassRegistry newRegistry() throws Exception {
        return new ClassRegistry(ExtensionApiLoader.loadDefault());
    }

    private record PhaseInput(
            @NotNull FrontendSourceUnit unit,
            @NotNull FrontendAnalysisData analysisData,
            @NotNull DiagnosticManager diagnostics
    ) {
    }
}
