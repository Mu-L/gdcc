package dev.superice.gdcc.frontend.lowering;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgRegion;
import dev.superice.gdcc.frontend.lowering.pass.FrontendLoweringAnalysisPass;
import dev.superice.gdcc.frontend.lowering.pass.FrontendLoweringBuildCfgPass;
import dev.superice.gdcc.frontend.lowering.pass.FrontendLoweringClassSkeletonPass;
import dev.superice.gdcc.frontend.lowering.pass.FrontendLoweringFunctionPreparationPass;
import dev.superice.gdcc.frontend.parse.FrontendModule;
import dev.superice.gdcc.frontend.parse.GdScriptParserService;
import dev.superice.gdcc.gdextension.ExtensionApiLoader;
import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdparser.frontend.ast.ExpressionStatement;
import dev.superice.gdparser.frontend.ast.FunctionDeclaration;
import dev.superice.gdparser.frontend.ast.PassStatement;
import dev.superice.gdparser.frontend.ast.ReturnStatement;
import dev.superice.gdparser.frontend.ast.VariableDeclaration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendLoweringBuildCfgPassTest {
    @Test
    void runPublishesFrontendCfgGraphForStraightLineExecutableBodiesAndKeepsLirShellOnly() throws Exception {
        var prepared = prepareContext(
                "build_cfg_straight_line.gd",
                """
                        class_name BuildCfgStraightLine
                        extends RefCounted
                        
                        var ready_value: int = 1
                        
                        func ping(seed: int) -> int:
                            pass
                            seed + 1
                            var local: int = seed + 2
                            return local
                            if seed:
                                pass
                        
                        func branchy(flag: bool) -> void:
                            if flag:
                                pass
                        """,
                Map.of("BuildCfgStraightLine", "RuntimeBuildCfgStraightLine")
        );

        new FrontendLoweringBuildCfgPass().run(prepared.context());

        var contexts = prepared.context().requireFunctionLoweringContexts();
        var straightLineContext = requireContext(
                contexts,
                FunctionLoweringContext.Kind.EXECUTABLE_BODY,
                "RuntimeBuildCfgStraightLine",
                "ping"
        );
        var structuredContext = requireContext(
                contexts,
                FunctionLoweringContext.Kind.EXECUTABLE_BODY,
                "RuntimeBuildCfgStraightLine",
                "branchy"
        );
        var propertyContext = requireContext(
                contexts,
                FunctionLoweringContext.Kind.PROPERTY_INIT,
                "RuntimeBuildCfgStraightLine",
                "_field_init_ready_value"
        );
        var sourceFile = prepared.module().units().getFirst().ast();
        var pingFunction = requireFunctionDeclaration(sourceFile, "ping");
        var pingBlock = pingFunction.body();
        var passStatement = assertInstanceOf(PassStatement.class, pingBlock.statements().get(0));
        var expressionStatement = assertInstanceOf(ExpressionStatement.class, pingBlock.statements().get(1));
        var variableDeclaration = assertInstanceOf(VariableDeclaration.class, pingBlock.statements().get(2));
        var returnStatement = assertInstanceOf(ReturnStatement.class, pingBlock.statements().get(3));
        var graph = straightLineContext.requireFrontendCfgGraph();
        var blockRegion = assertInstanceOf(
                FrontendCfgRegion.BlockRegion.class,
                straightLineContext.requireFrontendCfgRegion(pingBlock)
        );
        var entryNode = assertInstanceOf(FrontendCfgGraph.SequenceNode.class, graph.requireNode("seq_0"));
        var stopNode = assertInstanceOf(FrontendCfgGraph.StopNode.class, graph.requireNode("stop_0"));
        var entryItems = entryNode.items();

        assertAll(
                () -> assertFalse(prepared.diagnostics().hasErrors()),
                () -> assertEquals(List.of("seq_0", "stop_0"), graph.nodeIds()),
                () -> assertEquals("seq_0", graph.entryNodeId()),
                () -> assertEquals("seq_0", blockRegion.entryId()),
                () -> assertEquals("stop_0", entryNode.nextId()),
                () -> assertEquals(5, entryItems.size()),
                () -> assertSame(passStatement, assertInstanceOf(
                        FrontendCfgGraph.SourceAnchorItem.class,
                        entryItems.getFirst()
                ).statement()),
                () -> assertSame(expressionStatement.expression(), assertInstanceOf(
                        FrontendCfgGraph.OpaqueExprValueItem.class,
                        entryItems.get(1)
                ).expression()),
                () -> assertEquals("v0", assertInstanceOf(
                        FrontendCfgGraph.OpaqueExprValueItem.class,
                        entryItems.get(1)
                ).resultValueId()),
                () -> assertSame(variableDeclaration.value(), assertInstanceOf(
                        FrontendCfgGraph.OpaqueExprValueItem.class,
                        entryItems.get(2)
                ).expression()),
                () -> assertEquals("local_1", assertInstanceOf(
                        FrontendCfgGraph.OpaqueExprValueItem.class,
                        entryItems.get(2)
                ).resultValueId()),
                () -> assertSame(variableDeclaration, assertInstanceOf(
                        FrontendCfgGraph.LocalDeclarationItem.class,
                        entryItems.get(3)
                ).declaration()),
                () -> assertEquals("local_1", assertInstanceOf(
                        FrontendCfgGraph.LocalDeclarationItem.class,
                        entryItems.get(3)
                ).initializerValueIdOrNull()),
                () -> assertSame(returnStatement.value(), assertInstanceOf(
                        FrontendCfgGraph.OpaqueExprValueItem.class,
                        entryItems.get(4)
                ).expression()),
                () -> assertEquals("v2", assertInstanceOf(
                        FrontendCfgGraph.OpaqueExprValueItem.class,
                        entryItems.get(4)
                ).resultValueId()),
                () -> assertEquals("v2", stopNode.returnValueIdOrNull()),
                () -> assertEquals(0, straightLineContext.targetFunction().getBasicBlockCount()),
                () -> assertTrue(straightLineContext.targetFunction().getEntryBlockId().isEmpty()),
                () -> assertNull(structuredContext.frontendCfgGraphOrNull()),
                () -> assertNull(structuredContext.frontendCfgRegionOrNull(structuredContext.loweringRoot())),
                () -> assertNull(propertyContext.frontendCfgGraphOrNull()),
                () -> assertNull(propertyContext.frontendCfgRegionOrNull(propertyContext.loweringRoot())),
                () -> assertEquals(0, propertyContext.targetFunction().getBasicBlockCount()),
                () -> assertTrue(propertyContext.targetFunction().getEntryBlockId().isEmpty())
        );
    }

    @Test
    void runRejectsParameterDefaultContextsUntilTheirCompileSurfaceExists() throws Exception {
        var prepared = prepareContext(
                "build_cfg_parameter_default.gd",
                """
                        class_name BuildCfgParameterDefault
                        extends RefCounted
                        
                        func ping(value: int) -> int:
                            return value
                        """,
                Map.of("BuildCfgParameterDefault", "RuntimeBuildCfgParameterDefault")
        );
        var executableContext = requireContext(
                prepared.context().requireFunctionLoweringContexts(),
                FunctionLoweringContext.Kind.EXECUTABLE_BODY,
                "RuntimeBuildCfgParameterDefault",
                "ping"
        );
        var parameterDefaultContext = new FunctionLoweringContext(
                FunctionLoweringContext.Kind.PARAMETER_DEFAULT_INIT,
                executableContext.sourcePath(),
                executableContext.sourceClassRelation(),
                executableContext.owningClass(),
                executableContext.targetFunction(),
                executableContext.sourceOwner(),
                executableContext.loweringRoot(),
                executableContext.analysisData()
        );
        prepared.context().publishFunctionLoweringContexts(List.of(executableContext, parameterDefaultContext));

        var exception = assertThrows(
                IllegalStateException.class,
                () -> new FrontendLoweringBuildCfgPass().run(prepared.context())
        );

        assertTrue(exception.getMessage().contains("parameter default"), exception.getMessage());
    }

    private static @NotNull PreparedContext prepareContext(
            @NotNull String fileName,
            @NotNull String source,
            @NotNull Map<String, String> topLevelCanonicalNameMap
    ) throws Exception {
        var diagnostics = new DiagnosticManager();
        var module = parseModule(List.of(new SourceFixture(fileName, source)), topLevelCanonicalNameMap);
        var context = new FrontendLoweringContext(
                module,
                new ClassRegistry(ExtensionApiLoader.loadDefault()),
                diagnostics
        );
        new FrontendLoweringAnalysisPass().run(context);
        new FrontendLoweringClassSkeletonPass().run(context);
        new FrontendLoweringFunctionPreparationPass().run(context);
        return new PreparedContext(context, diagnostics, module);
    }

    private static @NotNull FrontendModule parseModule(
            @NotNull List<SourceFixture> fixtures,
            @NotNull Map<String, String> topLevelCanonicalNameMap
    ) {
        var parserService = new GdScriptParserService();
        var parseDiagnostics = new DiagnosticManager();
        var units = fixtures.stream()
                .map(fixture -> parserService.parseUnit(Path.of("tmp", fixture.fileName()), fixture.source(), parseDiagnostics))
                .toList();
        assertTrue(parseDiagnostics.isEmpty(), () -> "Unexpected parse diagnostics: " + parseDiagnostics.snapshot());
        return new FrontendModule("test_module", units, topLevelCanonicalNameMap);
    }

    private static @NotNull FunctionLoweringContext requireContext(
            @NotNull List<FunctionLoweringContext> contexts,
            @NotNull FunctionLoweringContext.Kind kind,
            @NotNull String owningClassName,
            @NotNull String functionName
    ) {
        return contexts.stream()
                .filter(context -> context.kind() == kind)
                .filter(context -> context.owningClass().getName().equals(owningClassName))
                .filter(context -> context.targetFunction().getName().equals(functionName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing context " + kind + " " + owningClassName + "." + functionName
                ));
    }

    private static @NotNull FunctionDeclaration requireFunctionDeclaration(
            @NotNull dev.superice.gdparser.frontend.ast.SourceFile sourceFile,
            @NotNull String functionName
    ) {
        return sourceFile.statements().stream()
                .filter(FunctionDeclaration.class::isInstance)
                .map(FunctionDeclaration.class::cast)
                .filter(function -> function.name().equals(functionName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing function declaration " + functionName));
    }

    private record PreparedContext(
            @NotNull FrontendLoweringContext context,
            @NotNull DiagnosticManager diagnostics,
            @NotNull FrontendModule module
    ) {
    }

    private record SourceFixture(
            @NotNull String fileName,
            @NotNull String source
    ) {
    }
}
