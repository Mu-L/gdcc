package dev.superice.gdcc.frontend.lowering.cfg;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.lowering.cfg.item.AssignmentItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.CallItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.LocalDeclarationItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.MemberLoadItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.OpaqueExprValueItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.SubscriptLoadItem;
import dev.superice.gdcc.frontend.lowering.cfg.region.FrontendCfgRegion;
import dev.superice.gdcc.frontend.lowering.cfg.region.FrontendElifRegion;
import dev.superice.gdcc.frontend.lowering.cfg.region.FrontendIfRegion;
import dev.superice.gdcc.frontend.lowering.cfg.region.FrontendWhileRegion;
import dev.superice.gdcc.frontend.parse.FrontendModule;
import dev.superice.gdcc.frontend.parse.GdScriptParserService;
import dev.superice.gdcc.frontend.sema.FrontendAnalysisData;
import dev.superice.gdcc.frontend.sema.analyzer.FrontendSemanticAnalyzer;
import dev.superice.gdcc.gdextension.ExtensionApiLoader;
import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdparser.frontend.ast.AssignmentExpression;
import dev.superice.gdparser.frontend.ast.AttributeCallStep;
import dev.superice.gdparser.frontend.ast.AttributeExpression;
import dev.superice.gdparser.frontend.ast.AttributePropertyStep;
import dev.superice.gdparser.frontend.ast.AttributeSubscriptStep;
import dev.superice.gdparser.frontend.ast.BinaryExpression;
import dev.superice.gdparser.frontend.ast.CallExpression;
import dev.superice.gdparser.frontend.ast.ExpressionStatement;
import dev.superice.gdparser.frontend.ast.FunctionDeclaration;
import dev.superice.gdparser.frontend.ast.IdentifierExpression;
import dev.superice.gdparser.frontend.ast.IfStatement;
import dev.superice.gdparser.frontend.ast.LiteralExpression;
import dev.superice.gdparser.frontend.ast.ReturnStatement;
import dev.superice.gdparser.frontend.ast.VariableDeclaration;
import dev.superice.gdparser.frontend.ast.WhileStatement;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendCfgGraphBuilderTest {
    @Test
    void buildExecutableBodyFailsFastForBreakWithoutLoopFrame() throws Exception {
        var analyzed = analyzeFunction(
                "cfg_builder_break_without_loop.gd",
                """
                        class_name CfgBuilderBreakWithoutLoop
                        extends RefCounted
                        
                        func ping() -> void:
                            break
                        """,
                "ping",
                Map.of("CfgBuilderBreakWithoutLoop", "RuntimeCfgBuilderBreakWithoutLoop")
        );

        var rootBlock = analyzed.function().body();
        var exception = assertThrows(
                IllegalStateException.class,
                () -> new FrontendCfgGraphBuilder().buildExecutableBody(rootBlock, analyzed.analysisData())
        );

        assertAll(
                () -> assertFalse(analyzed.diagnostics().hasErrors()),
                () -> assertTrue(exception.getMessage().contains("loop frame"), exception.getMessage())
        );
    }

    @Test
    void buildExecutableBodyStopsScanningAfterReachableReturn() throws Exception {
        var analyzed = analyzeFunction(
                "cfg_builder_terminal_return.gd",
                """
                        class_name CfgBuilderTerminalReturn
                        extends RefCounted
                        
                        func ping(seed: int) -> int:
                            return seed
                            if seed:
                                pass
                        """,
                "ping",
                Map.of("CfgBuilderTerminalReturn", "RuntimeCfgBuilderTerminalReturn")
        );

        var rootBlock = analyzed.function().body();
        var build = new FrontendCfgGraphBuilder().buildExecutableBody(rootBlock, analyzed.analysisData());
        var graph = build.graph();
        var entryNode = assertInstanceOf(FrontendCfgGraph.SequenceNode.class, graph.requireNode("seq_0"));
        var stopNode = assertInstanceOf(FrontendCfgGraph.StopNode.class, graph.requireNode("stop_1"));
        var returnStatement = assertInstanceOf(ReturnStatement.class, rootBlock.statements().getFirst());
        var returnValue = assertInstanceOf(OpaqueExprValueItem.class, entryNode.items().getFirst());
        var rootRegion = assertInstanceOf(FrontendCfgRegion.BlockRegion.class, build.regions().get(rootBlock));

        assertAll(
                () -> assertEquals(List.of("seq_0", "stop_1"), graph.nodeIds()),
                () -> assertEquals(1, entryNode.items().size()),
                () -> assertEquals("stop_1", entryNode.nextId()),
                () -> assertEquals("v0", stopNode.returnValueIdOrNull()),
                () -> assertEquals("seq_0", rootRegion.entryId()),
                () -> assertEquals(List.of(), returnValue.operandValueIds()),
                () -> assertEquals("v0", returnValue.resultValueId()),
                () -> assertEquals(returnStatement.value(), returnValue.expression())
        );
    }

    @Test
    void buildExecutableBodyRecursivelyExpandsNestedCallsSubscriptsAndMemberReads() throws Exception {
        var analyzed = analyzeFunction(
                "cfg_builder_nested_chain.gd",
                """
                        class_name CfgBuilderNested
                        extends RefCounted
                        
                        var payloads: Dictionary[int, CfgBuilderNested]
                        var value: int
                        
                        func helper(value: int) -> int:
                            return value + 1
                        
                        func build(seed: int) -> CfgBuilderNested:
                            return self
                        
                        func fetch(index: int) -> CfgBuilderNested:
                            return self
                        
                        func ping(seed: int) -> int:
                            return build(helper(seed)).payloads[helper(seed)].fetch(helper(seed)).value
                        """,
                "ping",
                Map.of("CfgBuilderNested", "RuntimeCfgBuilderNested")
        );

        var rootBlock = analyzed.function().body();
        var build = new FrontendCfgGraphBuilder().buildExecutableBody(rootBlock, analyzed.analysisData());
        var graph = build.graph();
        var entryNode = assertInstanceOf(FrontendCfgGraph.SequenceNode.class, graph.requireNode("seq_0"));
        var stopNode = assertInstanceOf(FrontendCfgGraph.StopNode.class, graph.requireNode("stop_1"));
        var returnStatement = assertInstanceOf(ReturnStatement.class, rootBlock.statements().getFirst());
        var returnExpression = assertInstanceOf(AttributeExpression.class, returnStatement.value());
        var buildCall = assertInstanceOf(CallExpression.class, returnExpression.base());
        var buildHelperCall = assertInstanceOf(CallExpression.class, buildCall.arguments().getFirst());
        var buildSeed = assertInstanceOf(IdentifierExpression.class, buildHelperCall.arguments().getFirst());
        var payloadsStep = assertInstanceOf(AttributeSubscriptStep.class, returnExpression.steps().getFirst());
        var payloadsHelperCall = assertInstanceOf(CallExpression.class, payloadsStep.arguments().getFirst());
        var payloadsSeed = assertInstanceOf(IdentifierExpression.class, payloadsHelperCall.arguments().getFirst());
        var fetchStep = assertInstanceOf(AttributeCallStep.class, returnExpression.steps().get(1));
        var fetchHelperCall = assertInstanceOf(CallExpression.class, fetchStep.arguments().getFirst());
        var fetchSeed = assertInstanceOf(IdentifierExpression.class, fetchHelperCall.arguments().getFirst());
        var valueStep = assertInstanceOf(AttributePropertyStep.class, returnExpression.steps().get(2));

        var items = entryNode.items();
        var buildSeedValue = assertInstanceOf(OpaqueExprValueItem.class, items.get(0));
        var buildHelperValue = assertInstanceOf(CallItem.class, items.get(1));
        var buildCallValue = assertInstanceOf(CallItem.class, items.get(2));
        var payloadsSeedValue = assertInstanceOf(OpaqueExprValueItem.class, items.get(3));
        var payloadsHelperValue = assertInstanceOf(CallItem.class, items.get(4));
        var payloadsSubscriptValue = assertInstanceOf(SubscriptLoadItem.class, items.get(5));
        var fetchSeedValue = assertInstanceOf(OpaqueExprValueItem.class, items.get(6));
        var fetchHelperValue = assertInstanceOf(CallItem.class, items.get(7));
        var fetchCallValue = assertInstanceOf(CallItem.class, items.get(8));
        var valueRead = assertInstanceOf(MemberLoadItem.class, items.get(9));

        assertAll(
                () -> assertFalse(analyzed.diagnostics().hasErrors()),
                () -> assertEquals(10, items.size()),
                () -> assertEquals("v9", stopNode.returnValueIdOrNull()),
                () -> assertEquals(buildSeed, buildSeedValue.expression()),
                () -> assertEquals(List.of(), buildSeedValue.operandValueIds()),
                () -> assertEquals("v0", buildSeedValue.resultValueId()),
                () -> assertEquals(buildHelperCall, buildHelperValue.anchor()),
                () -> assertEquals("helper", buildHelperValue.callableName()),
                () -> assertEquals(List.of("v0"), buildHelperValue.operandValueIds()),
                () -> assertEquals("v1", buildHelperValue.resultValueId()),
                () -> assertEquals(buildCall, buildCallValue.anchor()),
                () -> assertEquals("build", buildCallValue.callableName()),
                () -> assertEquals(List.of("v1"), buildCallValue.operandValueIds()),
                () -> assertEquals("v2", buildCallValue.resultValueId()),
                () -> assertEquals(payloadsSeed, payloadsSeedValue.expression()),
                () -> assertEquals("v3", payloadsSeedValue.resultValueId()),
                () -> assertEquals(payloadsHelperCall, payloadsHelperValue.anchor()),
                () -> assertEquals("helper", payloadsHelperValue.callableName()),
                () -> assertEquals(List.of("v3"), payloadsHelperValue.operandValueIds()),
                () -> assertEquals("v4", payloadsHelperValue.resultValueId()),
                () -> assertEquals(payloadsStep, payloadsSubscriptValue.anchor()),
                () -> assertEquals("payloads", payloadsSubscriptValue.memberNameOrNull()),
                () -> assertEquals(List.of("v2", "v4"), payloadsSubscriptValue.operandValueIds()),
                () -> assertEquals("v5", payloadsSubscriptValue.resultValueId()),
                () -> assertEquals(fetchSeed, fetchSeedValue.expression()),
                () -> assertEquals("v6", fetchSeedValue.resultValueId()),
                () -> assertEquals(fetchHelperCall, fetchHelperValue.anchor()),
                () -> assertEquals(List.of("v6"), fetchHelperValue.operandValueIds()),
                () -> assertEquals("v7", fetchHelperValue.resultValueId()),
                () -> assertEquals(fetchStep, fetchCallValue.anchor()),
                () -> assertEquals("fetch", fetchCallValue.callableName()),
                () -> assertEquals(List.of("v5", "v7"), fetchCallValue.operandValueIds()),
                () -> assertEquals("v8", fetchCallValue.resultValueId()),
                () -> assertEquals(valueStep, valueRead.anchor()),
                () -> assertEquals("value", valueRead.memberName()),
                () -> assertEquals(List.of("v8"), valueRead.operandValueIds()),
                () -> assertEquals("v9", valueRead.resultValueId())
        );
    }

    @Test
    void buildExecutableBodyKeepsDeclarationAndAssignmentCommitsExplicit() throws Exception {
        var analyzed = analyzeFunction(
                "cfg_builder_assignment_commit.gd",
                """
                        class_name CfgBuilderAssignment
                        extends RefCounted
                        
                        var items: Dictionary[int, int]
                        
                        func helper(value: int) -> int:
                            return value + 1
                        
                        func produce(value: int) -> int:
                            return value * 2
                        
                        func ping(seed: int) -> int:
                            var local: int = helper(seed) + 1
                            items[helper(seed)] = produce(seed)
                            return local
                        """,
                "ping",
                Map.of("CfgBuilderAssignment", "RuntimeCfgBuilderAssignment")
        );

        var rootBlock = analyzed.function().body();
        var build = new FrontendCfgGraphBuilder().buildExecutableBody(rootBlock, analyzed.analysisData());
        var graph = build.graph();
        var entryNode = assertInstanceOf(FrontendCfgGraph.SequenceNode.class, graph.requireNode("seq_0"));
        var stopNode = assertInstanceOf(FrontendCfgGraph.StopNode.class, graph.requireNode("stop_1"));

        var localDeclaration = assertInstanceOf(VariableDeclaration.class, rootBlock.statements().getFirst());
        var localInitializer = assertInstanceOf(BinaryExpression.class, localDeclaration.value());
        var localHelperCall = assertInstanceOf(CallExpression.class, localInitializer.left());
        var localSeed = assertInstanceOf(IdentifierExpression.class, localHelperCall.arguments().getFirst());
        var localLiteral = assertInstanceOf(LiteralExpression.class, localInitializer.right());

        var assignmentStatement = assertInstanceOf(ExpressionStatement.class, rootBlock.statements().get(1));
        var assignmentExpression = assertInstanceOf(AssignmentExpression.class, assignmentStatement.expression());
        var targetSubscript = assertInstanceOf(dev.superice.gdparser.frontend.ast.SubscriptExpression.class, assignmentExpression.left());
        var targetBase = assertInstanceOf(IdentifierExpression.class, targetSubscript.base());
        var indexHelperCall = assertInstanceOf(CallExpression.class, targetSubscript.arguments().getFirst());
        var indexSeed = assertInstanceOf(IdentifierExpression.class, indexHelperCall.arguments().getFirst());
        var rhsProduceCall = assertInstanceOf(CallExpression.class, assignmentExpression.right());
        var rhsSeed = assertInstanceOf(IdentifierExpression.class, rhsProduceCall.arguments().getFirst());

        var items = entryNode.items();
        var localSeedValue = assertInstanceOf(OpaqueExprValueItem.class, items.get(0));
        var localHelperValue = assertInstanceOf(CallItem.class, items.get(1));
        var localLiteralValue = assertInstanceOf(OpaqueExprValueItem.class, items.get(2));
        var localInitValue = assertInstanceOf(OpaqueExprValueItem.class, items.get(3));
        var localCommit = assertInstanceOf(LocalDeclarationItem.class, items.get(4));
        var targetBaseValue = assertInstanceOf(OpaqueExprValueItem.class, items.get(5));
        var indexSeedValue = assertInstanceOf(OpaqueExprValueItem.class, items.get(6));
        var indexHelperValue = assertInstanceOf(CallItem.class, items.get(7));
        var rhsSeedValue = assertInstanceOf(OpaqueExprValueItem.class, items.get(8));
        var rhsProduceValue = assertInstanceOf(CallItem.class, items.get(9));
        var assignmentCommit = assertInstanceOf(AssignmentItem.class, items.get(10));
        var returnValue = assertInstanceOf(OpaqueExprValueItem.class, items.get(11));

        assertAll(
                () -> assertFalse(analyzed.diagnostics().hasErrors()),
                () -> assertEquals(12, items.size()),
                () -> assertEquals(localSeed, localSeedValue.expression()),
                () -> assertEquals("v1", localSeedValue.resultValueId()),
                () -> assertEquals(localHelperCall, localHelperValue.anchor()),
                () -> assertEquals(List.of("v1"), localHelperValue.operandValueIds()),
                () -> assertEquals("v2", localHelperValue.resultValueId()),
                () -> assertEquals(localLiteral, localLiteralValue.expression()),
                () -> assertEquals("v3", localLiteralValue.resultValueId()),
                () -> assertEquals(localInitializer, localInitValue.expression()),
                () -> assertEquals(List.of("v2", "v3"), localInitValue.operandValueIds()),
                () -> assertEquals("local_0", localInitValue.resultValueId()),
                () -> assertEquals(localDeclaration, localCommit.declaration()),
                () -> assertEquals("local_0", localCommit.initializerValueIdOrNull()),
                () -> assertEquals(targetBase, targetBaseValue.expression()),
                () -> assertEquals("v4", targetBaseValue.resultValueId()),
                () -> assertEquals(indexSeed, indexSeedValue.expression()),
                () -> assertEquals("v5", indexSeedValue.resultValueId()),
                () -> assertEquals(indexHelperCall, indexHelperValue.anchor()),
                () -> assertEquals(List.of("v5"), indexHelperValue.operandValueIds()),
                () -> assertEquals("v6", indexHelperValue.resultValueId()),
                () -> assertEquals(rhsSeed, rhsSeedValue.expression()),
                () -> assertEquals("v7", rhsSeedValue.resultValueId()),
                () -> assertEquals(rhsProduceCall, rhsProduceValue.anchor()),
                () -> assertEquals("produce", rhsProduceValue.callableName()),
                () -> assertEquals(List.of("v7"), rhsProduceValue.operandValueIds()),
                () -> assertEquals("v8", rhsProduceValue.resultValueId()),
                () -> assertEquals(assignmentExpression, assignmentCommit.assignment()),
                () -> assertEquals(List.of("v4", "v6"), assignmentCommit.targetOperandValueIds()),
                () -> assertEquals("v8", assignmentCommit.rhsValueId()),
                () -> assertEquals(List.of("v4", "v6", "v8"), assignmentCommit.operandValueIds()),
                () -> assertEquals("v9", returnValue.resultValueId()),
                () -> assertEquals("v9", stopNode.returnValueIdOrNull())
        );
    }

    @Test
    void buildExecutableBodyKeepsDeclarationCommitExplicitWhenInitializerIsMissing() throws Exception {
        var analyzed = analyzeFunction(
                "cfg_builder_declaration_without_initializer.gd",
                """
                        class_name CfgBuilderDeclarationWithoutInitializer
                        extends RefCounted
                        
                        func ping() -> int:
                            var local: int
                            return 1
                        """,
                "ping",
                Map.of(
                        "CfgBuilderDeclarationWithoutInitializer",
                        "RuntimeCfgBuilderDeclarationWithoutInitializer"
                )
        );

        var rootBlock = analyzed.function().body();
        var build = new FrontendCfgGraphBuilder().buildExecutableBody(rootBlock, analyzed.analysisData());
        var graph = build.graph();
        var entryNode = assertInstanceOf(FrontendCfgGraph.SequenceNode.class, graph.requireNode("seq_0"));
        var declaration = assertInstanceOf(LocalDeclarationItem.class, entryNode.items().getFirst());
        var returnValue = assertInstanceOf(OpaqueExprValueItem.class, entryNode.items().get(1));

        assertAll(
                () -> assertEquals(2, entryNode.items().size()),
                () -> assertEquals("local", declaration.declaration().name()),
                () -> assertTrue(declaration.operandValueIds().isEmpty()),
                () -> assertEquals(List.of(), declaration.operandValueIds()),
                () -> assertEquals("v0", returnValue.resultValueId())
        );
    }

    @Test
    void buildExecutableBodyFailsFastWhenPublishedCallFactIsMissing() throws Exception {
        var analyzed = analyzeFunction(
                "cfg_builder_missing_call_fact.gd",
                """
                        class_name CfgBuilderMissingCallFact
                        extends RefCounted
                        
                        func helper(value: int) -> int:
                            return value + 1
                        
                        func ping(seed: int) -> int:
                            return helper(seed)
                        """,
                "ping",
                Map.of("CfgBuilderMissingCallFact", "RuntimeCfgBuilderMissingCallFact")
        );

        var rootBlock = analyzed.function().body();
        var returnStatement = assertInstanceOf(ReturnStatement.class, rootBlock.statements().getFirst());
        var helperCall = assertInstanceOf(CallExpression.class, returnStatement.value());
        analyzed.analysisData().resolvedCalls().remove(helperCall);

        var exception = assertThrows(
                IllegalStateException.class,
                () -> new FrontendCfgGraphBuilder().buildExecutableBody(rootBlock, analyzed.analysisData())
        );

        assertTrue(exception.getMessage().contains("resolvedCalls()"), exception.getMessage());
    }

    @Test
    void buildExecutableBodyFailsFastForShortCircuitBinaryBeforeEagerChildLowering() throws Exception {
        var analyzed = analyzeFunction(
                "cfg_builder_short_circuit_binary.gd",
                """
                        class_name CfgBuilderShortCircuitBinary
                        extends RefCounted
                        
                        func helper(value: int) -> bool:
                            return value > 0
                        
                        func ping(flag: bool, seed: int) -> bool:
                            return flag and helper(seed)
                        """,
                "ping",
                Map.of("CfgBuilderShortCircuitBinary", "RuntimeCfgBuilderShortCircuitBinary")
        );

        var rootBlock = analyzed.function().body();
        var returnStatement = assertInstanceOf(ReturnStatement.class, rootBlock.statements().getFirst());
        var shortCircuit = assertInstanceOf(BinaryExpression.class, returnStatement.value());
        var helperCall = assertInstanceOf(CallExpression.class, shortCircuit.right());
        analyzed.analysisData().resolvedCalls().remove(helperCall);

        var exception = assertThrows(
                IllegalStateException.class,
                () -> new FrontendCfgGraphBuilder().buildExecutableBody(rootBlock, analyzed.analysisData())
        );

        assertAll(
                () -> assertTrue(analyzed.diagnostics().hasErrors()),
                () -> assertTrue(exception.getMessage().contains("short-circuit"), exception.getMessage()),
                () -> assertTrue(exception.getMessage().contains("'and'"), exception.getMessage()),
                () -> assertFalse(exception.getMessage().contains("resolvedCalls()"), exception.getMessage())
        );
    }

    @Test
    void buildExecutableBodyPublishesIfElifElseRegionsAndSharedMerge() throws Exception {
        var analyzed = analyzeFunction(
                "cfg_builder_if_elif_else.gd",
                """
                        class_name CfgBuilderIfElifElse
                        extends RefCounted
                        
                        func ping(flag: bool, other: bool, payload: int) -> int:
                            if flag:
                                payload + 1
                            elif other:
                                return payload
                            else:
                                pass
                            return payload + 2
                        """,
                "ping",
                Map.of("CfgBuilderIfElifElse", "RuntimeCfgBuilderIfElifElse")
        );

        var rootBlock = analyzed.function().body();
        var outerIf = assertInstanceOf(IfStatement.class, rootBlock.statements().getFirst());
        var elifClause = outerIf.elifClauses().getFirst();
        var elseBody = Objects.requireNonNull(outerIf.elseBody(), "elseBody must not be null");
        var build = new FrontendCfgGraphBuilder().buildExecutableBody(rootBlock, analyzed.analysisData());
        var graph = build.graph();

        var rootRegion = assertInstanceOf(FrontendCfgRegion.BlockRegion.class, build.regions().get(rootBlock));
        var ifRegion = assertInstanceOf(FrontendIfRegion.class, build.regions().get(outerIf));
        var elifRegion = assertInstanceOf(FrontendElifRegion.class, build.regions().get(elifClause));
        var thenBlockRegion = assertInstanceOf(FrontendCfgRegion.BlockRegion.class, build.regions().get(outerIf.body()));
        var elseBlockRegion = assertInstanceOf(FrontendCfgRegion.BlockRegion.class, build.regions().get(elseBody));
        var thenEntry = assertInstanceOf(FrontendCfgGraph.SequenceNode.class, graph.requireNode(ifRegion.thenEntryId()));
        var mergeEntry = assertInstanceOf(FrontendCfgGraph.SequenceNode.class, graph.requireNode(ifRegion.mergeId()));
        var conditionEntry = assertInstanceOf(
                FrontendCfgGraph.SequenceNode.class,
                graph.requireNode(ifRegion.conditionEntryId())
        );
        var conditionBranch = assertInstanceOf(
                FrontendCfgGraph.BranchNode.class,
                graph.requireNode(conditionEntry.nextId())
        );
        var elifConditionEntry = assertInstanceOf(
                FrontendCfgGraph.SequenceNode.class,
                graph.requireNode(elifRegion.conditionEntryId())
        );
        var elifConditionBranch = assertInstanceOf(
                FrontendCfgGraph.BranchNode.class,
                graph.requireNode(elifConditionEntry.nextId())
        );
        var elifBodyEntry = assertInstanceOf(
                FrontendCfgGraph.SequenceNode.class,
                graph.requireNode(elifRegion.bodyEntryId())
        );
        var elseEntry = assertInstanceOf(
                FrontendCfgGraph.SequenceNode.class,
                graph.requireNode(elifRegion.nextClauseOrMergeId())
        );

        assertAll(
                () -> assertFalse(analyzed.diagnostics().hasErrors()),
                () -> assertEquals(ifRegion.conditionEntryId(), rootRegion.entryId()),
                () -> assertEquals(thenEntry.id(), conditionBranch.trueTargetId()),
                () -> assertEquals(ifRegion.elseOrNextClauseEntryId(), conditionBranch.falseTargetId()),
                () -> assertEquals(elifRegion.conditionEntryId(), ifRegion.elseOrNextClauseEntryId()),
                () -> assertEquals(elifBodyEntry.id(), elifConditionBranch.trueTargetId()),
                () -> assertEquals(elseEntry.id(), elifConditionBranch.falseTargetId()),
                () -> assertEquals(elseEntry.id(), elifRegion.nextClauseOrMergeId()),
                () -> assertEquals(thenEntry.id(), thenBlockRegion.entryId()),
                () -> assertEquals(elseEntry.id(), elseBlockRegion.entryId()),
                () -> assertEquals(ifRegion.mergeId(), thenEntry.nextId()),
                () -> assertEquals(ifRegion.mergeId(), elseEntry.nextId()),
                () -> assertInstanceOf(FrontendCfgGraph.StopNode.class, graph.requireNode(elifBodyEntry.nextId())),
                () -> assertInstanceOf(FrontendCfgGraph.StopNode.class, graph.requireNode(mergeEntry.nextId()))
        );
    }

    @Test
    void buildExecutableBodyPublishesWhileRegionAndLoopControlEdges() throws Exception {
        var analyzed = analyzeFunction(
                "cfg_builder_while_loop_control.gd",
                """
                        class_name CfgBuilderWhileLoopControl
                        extends RefCounted
                        
                        func ping(flag: bool, stop_now: bool, skip_now: bool, payload: int) -> int:
                            while flag:
                                if stop_now:
                                    break
                                if skip_now:
                                    continue
                                payload + 1
                            return payload
                        """,
                "ping",
                Map.of("CfgBuilderWhileLoopControl", "RuntimeCfgBuilderWhileLoopControl")
        );

        var rootBlock = analyzed.function().body();
        var whileStatement = assertInstanceOf(WhileStatement.class, rootBlock.statements().getFirst());
        var breakIf = assertInstanceOf(IfStatement.class, whileStatement.body().statements().getFirst());
        var continueIf = assertInstanceOf(IfStatement.class, whileStatement.body().statements().get(1));
        var build = new FrontendCfgGraphBuilder().buildExecutableBody(rootBlock, analyzed.analysisData());
        var graph = build.graph();

        var rootRegion = assertInstanceOf(FrontendCfgRegion.BlockRegion.class, build.regions().get(rootBlock));
        var whileRegion = assertInstanceOf(FrontendWhileRegion.class, build.regions().get(whileStatement));
        var breakIfRegion = assertInstanceOf(FrontendIfRegion.class, build.regions().get(breakIf));
        var continueIfRegion = assertInstanceOf(FrontendIfRegion.class, build.regions().get(continueIf));
        var conditionEntry = assertInstanceOf(
                FrontendCfgGraph.SequenceNode.class,
                graph.requireNode(whileRegion.conditionEntryId())
        );
        var conditionBranch = assertInstanceOf(
                FrontendCfgGraph.BranchNode.class,
                graph.requireNode(conditionEntry.nextId())
        );
        var breakThenEntry = assertInstanceOf(
                FrontendCfgGraph.SequenceNode.class,
                graph.requireNode(breakIfRegion.thenEntryId())
        );
        var continueThenEntry = assertInstanceOf(
                FrontendCfgGraph.SequenceNode.class,
                graph.requireNode(continueIfRegion.thenEntryId())
        );
        var bodyTail = assertInstanceOf(
                FrontendCfgGraph.SequenceNode.class,
                graph.requireNode(continueIfRegion.mergeId())
        );
        var exitEntry = assertInstanceOf(
                FrontendCfgGraph.SequenceNode.class,
                graph.requireNode(whileRegion.exitId())
        );

        assertAll(
                () -> assertFalse(analyzed.diagnostics().hasErrors()),
                () -> assertEquals(whileRegion.conditionEntryId(), rootRegion.entryId()),
                () -> assertEquals(whileRegion.bodyEntryId(), conditionBranch.trueTargetId()),
                () -> assertEquals(whileRegion.exitId(), conditionBranch.falseTargetId()),
                () -> assertEquals(whileRegion.exitId(), breakThenEntry.nextId()),
                () -> assertEquals(whileRegion.conditionEntryId(), continueThenEntry.nextId()),
                () -> assertEquals(whileRegion.conditionEntryId(), bodyTail.nextId()),
                () -> assertEquals(breakIfRegion.mergeId(), breakIfRegion.elseOrNextClauseEntryId()),
                () -> assertEquals(continueIfRegion.mergeId(), continueIfRegion.elseOrNextClauseEntryId()),
                () -> assertInstanceOf(FrontendCfgGraph.StopNode.class, graph.requireNode(exitEntry.nextId()))
        );
    }

    @Test
    void buildExecutableBodyDoesNotPublishLexicalRemainderAfterFullyTerminatingIfChain() throws Exception {
        var analyzed = analyzeFunction(
                "cfg_builder_terminating_if_chain.gd",
                """
                        class_name CfgBuilderTerminatingIfChain
                        extends RefCounted
                        
                        func ping(flag: bool, payload: int) -> int:
                            if flag:
                                return payload
                            else:
                                return payload + 1
                            payload + 2
                        """,
                "ping",
                Map.of("CfgBuilderTerminatingIfChain", "RuntimeCfgBuilderTerminatingIfChain")
        );

        var rootBlock = analyzed.function().body();
        var outerIf = assertInstanceOf(IfStatement.class, rootBlock.statements().getFirst());
        var trailingExpression = assertInstanceOf(ExpressionStatement.class, rootBlock.statements().get(1)).expression();
        var build = new FrontendCfgGraphBuilder().buildExecutableBody(rootBlock, analyzed.analysisData());
        var graph = build.graph();
        var ifRegion = assertInstanceOf(FrontendIfRegion.class, build.regions().get(outerIf));

        var containsTrailingExpression = graph.nodes().values().stream()
                .filter(FrontendCfgGraph.SequenceNode.class::isInstance)
                .map(FrontendCfgGraph.SequenceNode.class::cast)
                .flatMap(node -> node.items().stream())
                .filter(OpaqueExprValueItem.class::isInstance)
                .map(OpaqueExprValueItem.class::cast)
                .anyMatch(item -> item.expression() == trailingExpression);

        assertAll(
                () -> assertFalse(analyzed.diagnostics().hasErrors()),
                () -> assertInstanceOf(FrontendCfgGraph.StopNode.class, graph.requireNode(ifRegion.mergeId())),
                () -> assertFalse(containsTrailingExpression)
        );
    }

    @Test
    void buildExecutableBodyFailsFastForContinueWithoutLoopFrame() throws Exception {
        var analyzed = analyzeFunction(
                "cfg_builder_continue_without_loop.gd",
                """
                        class_name CfgBuilderContinueWithoutLoop
                        extends RefCounted
                        
                        func ping() -> void:
                            continue
                        """,
                "ping",
                Map.of("CfgBuilderContinueWithoutLoop", "RuntimeCfgBuilderContinueWithoutLoop")
        );

        var exception = assertThrows(
                IllegalStateException.class,
                () -> new FrontendCfgGraphBuilder().buildExecutableBody(
                        analyzed.function().body(),
                        analyzed.analysisData()
                )
        );

        assertAll(
                () -> assertFalse(analyzed.diagnostics().hasErrors()),
                () -> assertTrue(exception.getMessage().contains("loop frame"), exception.getMessage())
        );
    }

    private static @NotNull AnalyzedFunction analyzeFunction(
            @NotNull String fileName,
            @NotNull String source,
            @NotNull String functionName,
            @NotNull Map<String, String> topLevelCanonicalNameMap
    ) throws Exception {
        var module = parseModule(fileName, source, topLevelCanonicalNameMap);
        var diagnostics = new DiagnosticManager();
        var analysisData = new FrontendSemanticAnalyzer().analyzeForCompile(
                module,
                new ClassRegistry(ExtensionApiLoader.loadDefault()),
                diagnostics
        );
        return new AnalyzedFunction(
                module,
                diagnostics,
                analysisData,
                requireFunctionDeclaration(module.units().getFirst().ast(), functionName)
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

    private record AnalyzedFunction(
            @NotNull FrontendModule module,
            @NotNull DiagnosticManager diagnostics,
            @NotNull FrontendAnalysisData analysisData,
            @NotNull FunctionDeclaration function
    ) {
    }
}
