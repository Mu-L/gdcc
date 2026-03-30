package dev.superice.gdcc.frontend.lowering.cfg;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.parse.FrontendModule;
import dev.superice.gdcc.frontend.parse.GdScriptParserService;
import dev.superice.gdparser.frontend.ast.FunctionDeclaration;
import dev.superice.gdparser.frontend.ast.VariableDeclaration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendCfgGraphBuilderTest {
    @Test
    void buildStraightLineExecutableBodyRejectsReachableUnsupportedStatement() {
        var rootBlock = requireFunctionDeclaration(
                parseModule(
                        "cfg_builder_unsupported.gd",
                        """
                                class_name CfgBuilderUnsupported
                                extends RefCounted
                                
                                func ping(flag: bool) -> void:
                                    if flag:
                                        pass
                                """,
                        Map.of()
                ).units().getFirst().ast(),
                "ping"
        ).body();

        var exception = assertThrows(
                IllegalStateException.class,
                () -> new FrontendCfgGraphBuilder().buildStraightLineExecutableBody(rootBlock)
        );

        assertAll(
                () -> assertFalse(FrontendCfgGraphBuilder.supportsStraightLineExecutableBody(rootBlock)),
                () -> assertTrue(exception.getMessage().contains("IfStatement"), exception.getMessage())
        );
    }

    @Test
    void buildStraightLineExecutableBodyStopsScanningAfterReachableReturn() {
        var rootBlock = requireFunctionDeclaration(
                parseModule(
                        "cfg_builder_terminal_return.gd",
                        """
                                class_name CfgBuilderTerminalReturn
                                extends RefCounted
                                
                                func ping(seed: int) -> int:
                                    return seed
                                    if seed:
                                        pass
                                """,
                        Map.of()
                ).units().getFirst().ast(),
                "ping"
        ).body();

        var build = new FrontendCfgGraphBuilder().buildStraightLineExecutableBody(rootBlock);
        var graph = build.graph();
        var entryNode = (FrontendCfgGraph.SequenceNode) graph.requireNode("seq_0");
        var stopNode = (FrontendCfgGraph.StopNode) graph.requireNode("stop_0");

        assertAll(
                () -> assertTrue(FrontendCfgGraphBuilder.supportsStraightLineExecutableBody(rootBlock)),
                () -> assertEquals(List.of("seq_0", "stop_0"), graph.nodeIds()),
                () -> assertEquals(1, entryNode.items().size()),
                () -> assertEquals("stop_0", entryNode.nextId()),
                () -> assertEquals("v0", stopNode.returnValueIdOrNull()),
                () -> assertEquals("seq_0", build.rootRegion().entryId())
        );
    }

    @Test
    void buildStraightLineExecutableBodyUsesDeclarationDerivedIdsForVariableInitializers() {
        var rootBlock = requireFunctionDeclaration(
                parseModule(
                        "cfg_builder_variable_ids.gd",
                        """
                                class_name CfgBuilderVariableIds
                                extends RefCounted
                                
                                func ping(seed: int) -> int:
                                    var local: int = seed + 1
                                    var other: int = local + 1
                                    return other
                                """,
                        Map.of()
                ).units().getFirst().ast(),
                "ping"
        ).body();

        var build = new FrontendCfgGraphBuilder().buildStraightLineExecutableBody(rootBlock);
        var graph = build.graph();
        var entryNode = (FrontendCfgGraph.SequenceNode) graph.requireNode("seq_0");
        var initializer0 = (FrontendCfgGraph.OpaqueExprValueItem) entryNode.items().get(0);
        var declaration0 = (FrontendCfgGraph.LocalDeclarationItem) entryNode.items().get(1);
        var initializer1 = (FrontendCfgGraph.OpaqueExprValueItem) entryNode.items().get(2);
        var declaration1 = (FrontendCfgGraph.LocalDeclarationItem) entryNode.items().get(3);
        var stopNode = (FrontendCfgGraph.StopNode) graph.requireNode("stop_0");

        assertAll(
                () -> assertEquals(5, entryNode.items().size()),
                () -> assertEquals("local_0", initializer0.resultValueId()),
                () -> assertEquals("other_1", initializer1.resultValueId()),
                () -> assertEquals("v2", stopNode.returnValueIdOrNull()),
                () -> assertEquals("local", declaration0.declaration().name()),
                () -> assertEquals("local_0", declaration0.initializerValueIdOrNull()),
                () -> assertEquals("other", declaration1.declaration().name()),
                () -> assertEquals("other_1", declaration1.initializerValueIdOrNull())
        );
    }

    @Test
    void buildStraightLineExecutableBodyKeepsDeclarationCommitExplicitWhenInitializerIsMissing() {
        var rootBlock = requireFunctionDeclaration(
                parseModule(
                        "cfg_builder_declaration_without_initializer.gd",
                        """
                                class_name CfgBuilderDeclarationWithoutInitializer
                                extends RefCounted
                                
                                func ping() -> int:
                                    var local: int
                                    return 1
                                """,
                        Map.of()
                ).units().getFirst().ast(),
                "ping"
        ).body();

        var build = new FrontendCfgGraphBuilder().buildStraightLineExecutableBody(rootBlock);
        var graph = build.graph();
        var entryNode = (FrontendCfgGraph.SequenceNode) graph.requireNode("seq_0");
        var declaration = assertInstanceOf(FrontendCfgGraph.LocalDeclarationItem.class, entryNode.items().getFirst());
        var returnValue = assertInstanceOf(FrontendCfgGraph.OpaqueExprValueItem.class, entryNode.items().get(1));

        assertAll(
                () -> assertEquals(2, entryNode.items().size()),
                () -> assertEquals("local", declaration.declaration().name()),
                () -> assertTrue(declaration.operandValueIds().isEmpty()),
                () -> assertEquals(List.of(), declaration.operandValueIds()),
                () -> assertEquals("v0", returnValue.resultValueId())
        );
    }

    private static @NotNull FrontendModule parseModule(
            @NotNull String fileName,
            @NotNull String source,
            @NotNull Map<String, String> topLevelCanonicalNameMap
    ) {
        var parserService = new GdScriptParserService();
        var parseDiagnostics = new DiagnosticManager();
        var unit = parserService.parseUnit(Path.of("tmp", fileName), source, parseDiagnostics);
        assertTrue(parseDiagnostics.isEmpty(), () -> "Unexpected parse diagnostics: " + parseDiagnostics.snapshot());
        return new FrontendModule("test_module", List.of(unit), topLevelCanonicalNameMap);
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
}
