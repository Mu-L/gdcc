package dev.superice.gdcc.frontend.lowering.cfg;

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
        var sequenceItems = new ArrayList<FrontendCfgGraph.SequenceItem>();
        sequenceItems.add(new FrontendCfgGraph.SourceAnchorItem(new PassStatement(SYNTHETIC_RANGE)));
        sequenceItems.add(new FrontendCfgGraph.OpaqueExprValueItem(identifier("flag"), "v0"));

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
        var sourceAnchor = new FrontendCfgGraph.SourceAnchorItem(passStatement);
        var opaqueValue = new FrontendCfgGraph.OpaqueExprValueItem(expression, "v0");
        var callItem = new FrontendCfgGraph.CallItem(expression, "recv0", List.of("arg0", "arg1"), "v1");

        assertAll(
                () -> assertSame(passStatement, sourceAnchor.statement()),
                () -> assertSame(passStatement, sourceAnchor.anchor()),
                () -> assertSame(expression, opaqueValue.expression()),
                () -> assertSame(expression, opaqueValue.anchor()),
                () -> assertEquals("v0", opaqueValue.resultValueIdOrNull()),
                () -> assertEquals(List.of(), opaqueValue.operandValueIds()),
                () -> assertSame(expression, callItem.anchor()),
                () -> assertEquals("v1", callItem.resultValueIdOrNull()),
                () -> assertEquals(List.of("recv0", "arg0", "arg1"), callItem.operandValueIds())
        );
    }

    @Test
    void valueOpItemsRejectBlankValueIds() {
        var blankOpaque = assertThrows(
                IllegalArgumentException.class,
                () -> new FrontendCfgGraph.OpaqueExprValueItem(identifier("seed"), " ")
        );
        var blankCallOperand = assertThrows(
                IllegalArgumentException.class,
                () -> new FrontendCfgGraph.CallItem(identifier("seed"), "recv0", List.of("arg0", " "), "v1")
        );

        assertAll(
                () -> assertTrue(blankOpaque.getMessage().contains("resultValueId")),
                () -> assertTrue(blankCallOperand.getMessage().contains("argumentValueIds[1]"))
        );
    }

    private static IdentifierExpression identifier(String name) {
        return new IdentifierExpression(name, SYNTHETIC_RANGE);
    }
}
