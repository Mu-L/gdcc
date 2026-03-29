package dev.superice.gdcc.frontend.lowering.cfg;

import dev.superice.gdparser.frontend.ast.Expression;
import dev.superice.gdparser.frontend.ast.Statement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Immutable frontend-only CFG graph published after source control-flow has been materialized.
///
/// This graph intentionally stays independent from LIR basic blocks:
/// - node ids are frontend-local stable names
/// - `BranchNode` keeps the source condition root and value id without forcing bool normalization
/// - `SequenceNode` carries linear source evaluation items instead of already-lowered instructions
///
/// The graph is expected to be fully connected when it is published into a
/// `FunctionLoweringContext`, so the constructor validates entry/successor references eagerly.
public record FrontendCfgGraph(
        @NotNull String entryNodeId,
        @NotNull Map<String, Node> nodes
) {
    public FrontendCfgGraph {
        entryNodeId = validateNodeId(entryNodeId, "entryNodeId");
        nodes = copyNodes(nodes);
        validateEntryNode(nodes, entryNodeId);
        validateSuccessorTargets(nodes);
    }

    public boolean hasNode(@NotNull String nodeId) {
        return nodes.containsKey(validateNodeId(nodeId, "nodeId"));
    }

    public @Nullable Node nodeOrNull(@NotNull String nodeId) {
        return nodes.get(validateNodeId(nodeId, "nodeId"));
    }

    public @NotNull Node requireNode(@NotNull String nodeId) {
        var node = nodeOrNull(nodeId);
        if (node == null) {
            throw new IllegalStateException("Frontend CFG node has not been published: " + nodeId);
        }
        return node;
    }

    public @NotNull List<String> nodeIds() {
        return List.copyOf(nodes.keySet());
    }

    /// One frontend CFG node.
    public sealed interface Node permits SequenceNode, BranchNode, StopNode {
        @NotNull String id();
    }

    /// One linear item executed inside a `SequenceNode`.
    ///
    /// The first implementation round only needs statement passthrough and expression evaluation
    /// steps, but the sealed shape keeps room for future builder-local items without reworking the
    /// graph carrier contract.
    public sealed interface SequenceItem permits StatementItem, EvalExprItem {
    }

    /// Sequence node for straight-line execution.
    ///
    /// `nextId` always names the lexical continuation node after all items in this sequence have
    /// executed.
    public record SequenceNode(
            @NotNull String id,
            @NotNull List<SequenceItem> items,
            @NotNull String nextId
    ) implements Node {
        public SequenceNode {
            id = validateNodeId(id, "id");
            items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
            nextId = validateNodeId(nextId, "nextId");
        }
    }

    /// Branch node for one source-level condition split.
    ///
    /// `conditionValueId` is the value already computed by a preceding sequence. It may still be a
    /// non-bool source value; truthiness normalization is deferred to frontend CFG -> LIR lowering.
    public record BranchNode(
            @NotNull String id,
            @NotNull Expression conditionRoot,
            @NotNull String conditionValueId,
            @NotNull String trueTargetId,
            @NotNull String falseTargetId
    ) implements Node {
        public BranchNode {
            id = validateNodeId(id, "id");
            conditionValueId = validateNodeId(conditionValueId, "conditionValueId");
            trueTargetId = validateNodeId(trueTargetId, "trueTargetId");
            falseTargetId = validateNodeId(falseTargetId, "falseTargetId");
        }
    }

    /// Terminal node that ends frontend control flow for the current function.
    public record StopNode(
            @NotNull String id,
            @Nullable String returnValueIdOrNull
    ) implements Node {
        public StopNode {
            id = validateNodeId(id, "id");
            returnValueIdOrNull = validateOptionalNodeId(returnValueIdOrNull, "returnValueIdOrNull");
        }
    }

    /// Linear passthrough of one source statement.
    public record StatementItem(@NotNull Statement statement) implements SequenceItem {
    }

    /// Linear evaluation of one expression into a frontend-local value id.
    public record EvalExprItem(
            @NotNull Expression expression,
            @NotNull String resultValueId
    ) implements SequenceItem {
        public EvalExprItem {
            resultValueId = validateNodeId(resultValueId, "resultValueId");
        }
    }

    private static @NotNull Map<String, Node> copyNodes(@NotNull Map<String, Node> nodes) {
        Objects.requireNonNull(nodes, "nodes must not be null");
        var copiedNodes = new LinkedHashMap<String, Node>(nodes.size());
        for (var entry : nodes.entrySet()) {
            var nodeId = validateNodeId(entry.getKey(), "nodeId");
            var node = Objects.requireNonNull(entry.getValue(), "node must not be null");
            if (!node.id().equals(nodeId)) {
                throw new IllegalArgumentException(
                        "Frontend CFG node id mismatch: key '" + nodeId + "' does not match node.id '" + node.id() + "'"
                );
            }
            copiedNodes.put(nodeId, node);
        }
        return Collections.unmodifiableMap(copiedNodes);
    }

    private static void validateEntryNode(@NotNull Map<String, Node> nodes, @NotNull String entryNodeId) {
        if (!nodes.containsKey(entryNodeId)) {
            throw new IllegalArgumentException("Frontend CFG entry node does not exist: " + entryNodeId);
        }
    }

    private static void validateSuccessorTargets(@NotNull Map<String, Node> nodes) {
        for (var node : nodes.values()) {
            switch (node) {
                case SequenceNode(_, _, var nextId) -> validateTargetNode(nodes, node.id(), "nextId", nextId);
                case BranchNode(_, _, _, var trueTargetId, var falseTargetId) -> {
                    validateTargetNode(nodes, node.id(), "trueTargetId", trueTargetId);
                    validateTargetNode(nodes, node.id(), "falseTargetId", falseTargetId);
                }
                case StopNode _ -> {
                }
            }
        }
    }

    private static void validateTargetNode(
            @NotNull Map<String, Node> nodes,
            @NotNull String sourceNodeId,
            @NotNull String edgeName,
            @NotNull String targetNodeId
    ) {
        if (!nodes.containsKey(targetNodeId)) {
            throw new IllegalArgumentException(
                    "Frontend CFG node '" + sourceNodeId + "' references missing " + edgeName + " '" + targetNodeId + "'"
            );
        }
    }

    static @NotNull String validateNodeId(@Nullable String id, @NotNull String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        var nonNullId = Objects.requireNonNull(id, fieldName + " must not be null");
        if (nonNullId.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return nonNullId;
    }

    private static @Nullable String validateOptionalNodeId(@Nullable String id, @NotNull String fieldName) {
        return id == null ? null : validateNodeId(id, fieldName);
    }
}
