package dev.superice.gdcc.frontend.lowering.cfg;

import dev.superice.gdcc.frontend.lowering.cfg.item.AssignmentItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.CallItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.MemberLoadItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.OpaqueExprValueItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.SequenceItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.SourceAnchorItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.SubscriptLoadItem;
import dev.superice.gdparser.frontend.ast.AssignmentExpression;
import dev.superice.gdparser.frontend.ast.AttributePropertyStep;
import dev.superice.gdparser.frontend.ast.IdentifierExpression;
import dev.superice.gdparser.frontend.ast.PassStatement;
import dev.superice.gdparser.frontend.ast.Point;
import dev.superice.gdparser.frontend.ast.Range;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendCfgGraphTest {
    private static final Range SYNTHETIC_RANGE = new Range(0, 1, new Point(0, 0), new Point(0, 1));

    @Test
    void constructorCopiesNodeTopologyAndSupportsTypedLookup() {
        var sequenceItems = new ArrayList<SequenceItem>();
        sequenceItems.add(new SourceAnchorItem(new PassStatement(SYNTHETIC_RANGE)));
        sequenceItems.add(new OpaqueExprValueItem(identifier("flag"), "v0"));

        var nodes = new LinkedHashMap<String, FrontendCfgGraph.NodeDef>();
        nodes.put("entry", new FrontendCfgGraph.SequenceNode("entry", sequenceItems, "branch"));
        nodes.put("branch", new FrontendCfgGraph.BranchNode("branch", identifier("flag"), "v0", "then", "else"));
        nodes.put("then", new FrontendCfgGraph.StopNode("then", "ret0"));
        nodes.put("else", new FrontendCfgGraph.StopNode("else", null));

        var graph = new FrontendCfgGraph("entry", nodes);
        sequenceItems.clear();
        nodes.clear();

        var entryNode = assertInstanceOf(FrontendCfgGraph.SequenceNode.class, graph.requireNode("entry"));
        var branchNode = assertInstanceOf(FrontendCfgGraph.BranchNode.class, graph.requireNode("branch"));
        var thenNode = assertInstanceOf(FrontendCfgGraph.StopNode.class, graph.requireNode("then"));

        assertAll(
                () -> assertEquals(List.of("entry", "branch", "then", "else"), graph.nodeIds()),
                () -> assertEquals(4, graph.nodes().size()),
                () -> assertTrue(graph.hasNode("entry")),
                () -> assertEquals(2, entryNode.items().size()),
                () -> assertEquals("branch", entryNode.nextId()),
                () -> assertEquals("v0", branchNode.conditionValueId()),
                () -> assertEquals(List.of("then", "else"), List.of(branchNode.trueTargetId(), branchNode.falseTargetId())),
                () -> assertEquals("ret0", thenNode.returnValueIdOrNull()),
                () -> assertNull(graph.nodeOrNull("missing")),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> graph.nodes().put("extra", new FrontendCfgGraph.StopNode("extra", null))
                )
        );
    }

    @Test
    void constructorRejectsBrokenEntryAndEdgeContracts() {
        var missingEntryNodes = new LinkedHashMap<String, FrontendCfgGraph.NodeDef>();
        missingEntryNodes.put("stop", new FrontendCfgGraph.StopNode("stop", null));

        var keyMismatchNodes = new LinkedHashMap<String, FrontendCfgGraph.NodeDef>();
        keyMismatchNodes.put("entry", new FrontendCfgGraph.StopNode("other", null));

        var brokenTargetNodes = new LinkedHashMap<String, FrontendCfgGraph.NodeDef>();
        brokenTargetNodes.put("entry", new FrontendCfgGraph.SequenceNode("entry", List.of(), "branch"));
        brokenTargetNodes.put("branch", new FrontendCfgGraph.BranchNode("branch", identifier("flag"), "v0", "then", "missing"));
        brokenTargetNodes.put("then", new FrontendCfgGraph.StopNode("then", null));

        var missingEntry = assertThrows(
                IllegalArgumentException.class,
                () -> new FrontendCfgGraph("missing", missingEntryNodes)
        );
        var keyMismatch = assertThrows(
                IllegalArgumentException.class,
                () -> new FrontendCfgGraph("entry", keyMismatchNodes)
        );
        var brokenTarget = assertThrows(
                IllegalArgumentException.class,
                () -> new FrontendCfgGraph("entry", brokenTargetNodes)
        );

        assertAll(
                () -> assertTrue(missingEntry.getMessage().contains("entry node")),
                () -> assertTrue(keyMismatch.getMessage().contains("node id mismatch")),
                () -> assertTrue(brokenTarget.getMessage().contains("missing falseTargetId"))
        );
    }

    @Test
    void valueOpItemsExposeStableAnchorOperandAndResultContracts() {
        var passStatement = new PassStatement(SYNTHETIC_RANGE);
        var expression = identifier("seed");
        var sourceAnchor = new SourceAnchorItem(passStatement);
        var propertyStep = new AttributePropertyStep("payload", SYNTHETIC_RANGE);
        var assignmentExpression = new AssignmentExpression("=", identifier("target"), expression, SYNTHETIC_RANGE);
        var opaqueValue = new OpaqueExprValueItem(expression, List.of("lhs0", "rhs0"), "v0");
        var memberItem = new MemberLoadItem(propertyStep, "payload", "recv0", "v1");
        var subscriptItem = new SubscriptLoadItem(expression, "items", "recv1", List.of("arg0"), "v2");
        var callItem = new CallItem(expression, "build", "recv2", List.of("arg1", "arg2"), "v3");
        var assignmentItem = new AssignmentItem(assignmentExpression, List.of("slot0", "index0"), "rhs3", null);

        assertAll(
                () -> assertSame(passStatement, sourceAnchor.statement()),
                () -> assertSame(passStatement, sourceAnchor.anchor()),
                () -> assertSame(expression, opaqueValue.expression()),
                () -> assertSame(expression, opaqueValue.anchor()),
                () -> assertEquals(List.of("lhs0", "rhs0"), opaqueValue.operandValueIds()),
                () -> assertEquals("v0", opaqueValue.resultValueIdOrNull()),
                () -> assertSame(propertyStep, memberItem.anchor()),
                () -> assertEquals("payload", memberItem.memberName()),
                () -> assertEquals(List.of("recv0"), memberItem.operandValueIds()),
                () -> assertSame(expression, subscriptItem.anchor()),
                () -> assertEquals("items", subscriptItem.memberNameOrNull()),
                () -> assertEquals(List.of("recv1", "arg0"), subscriptItem.operandValueIds()),
                () -> assertSame(expression, callItem.anchor()),
                () -> assertEquals("build", callItem.callableName()),
                () -> assertEquals("v3", callItem.resultValueIdOrNull()),
                () -> assertEquals(List.of("recv2", "arg1", "arg2"), callItem.operandValueIds()),
                () -> assertSame(assignmentExpression, assignmentItem.anchor()),
                () -> assertEquals(List.of("slot0", "index0", "rhs3"), assignmentItem.operandValueIds()),
                () -> assertNull(assignmentItem.resultValueIdOrNull())
        );
    }

    @Test
    void valueOpItemsRejectBlankValueIds() {
        var blankOpaque = assertThrows(
                IllegalArgumentException.class,
                () -> new OpaqueExprValueItem(identifier("seed"), " ")
        );
        var blankCallOperand = assertThrows(
                IllegalArgumentException.class,
                () -> new CallItem(identifier("seed"), "build", "recv0", List.of("arg0", " "), "v1")
        );
        var blankMemberName = assertThrows(
                IllegalArgumentException.class,
                () -> new MemberLoadItem(identifier("seed"), " ", "recv0", "v2")
        );

        assertAll(
                () -> assertTrue(blankOpaque.getMessage().contains("resultValueId")),
                () -> assertTrue(blankCallOperand.getMessage().contains("argumentValueIds[1]")),
                () -> assertTrue(blankMemberName.getMessage().contains("memberName"))
        );
    }

    private static IdentifierExpression identifier(String name) {
        return new IdentifierExpression(name, SYNTHETIC_RANGE);
    }
}
