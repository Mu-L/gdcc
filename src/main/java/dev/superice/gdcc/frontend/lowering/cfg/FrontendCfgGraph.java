package dev.superice.gdcc.frontend.lowering.cfg;

import dev.superice.gdparser.frontend.ast.AssignmentExpression;
import dev.superice.gdparser.frontend.ast.CastExpression;
import dev.superice.gdparser.frontend.ast.Expression;
import dev.superice.gdparser.frontend.ast.Node;
import dev.superice.gdparser.frontend.ast.Statement;
import dev.superice.gdparser.frontend.ast.TypeTestExpression;
import dev.superice.gdparser.frontend.ast.VariableDeclaration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
        @NotNull Map<String, NodeDef> nodes
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

    public @Nullable NodeDef nodeOrNull(@NotNull String nodeId) {
        return nodes.get(validateNodeId(nodeId, "nodeId"));
    }

    public @NotNull NodeDef requireNode(@NotNull String nodeId) {
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
    public sealed interface NodeDef permits SequenceNode, BranchNode, StopNode {
        @NotNull String id();
    }

    /// One linear item executed inside a `SequenceNode`.
    ///
    /// `SequenceNode` intentionally separates pure source anchors from real value-producing or
    /// state-mutating operations so later lowering passes no longer need to infer execution meaning
    /// from raw statement AST pairing.
    public sealed interface SequenceItem permits SourceAnchorItem, ValueOpItem {
        @NotNull Node anchor();
    }

    /// Executing frontend value-op item.
    ///
    /// All real execution work published into the frontend CFG goes through this shape:
    /// - `anchor()` keeps the source AST root that owns the operation
    /// - `resultValueIdOrNull()` names the value produced by this item when it is value-producing
    /// - `operandValueIds()` lists already-materialized inputs consumed by this item in source order
    public sealed interface ValueOpItem extends SequenceItem permits OpaqueExprValueItem, LocalDeclarationItem,
            AssignmentItem, MemberLoadItem, SubscriptLoadItem, CallItem, CastItem, TypeTestItem {
        @Nullable String resultValueIdOrNull();

        @NotNull List<String> operandValueIds();
    }

    /// Sequence node for straight-line execution.
    ///
    /// `nextId` always names the lexical continuation node after all items in this sequence have
    /// executed.
    public record SequenceNode(
            @NotNull String id,
            @NotNull List<SequenceItem> items,
            @NotNull String nextId
    ) implements NodeDef {
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
    ) implements NodeDef {
        public BranchNode {
            id = validateNodeId(id, "id");
            Objects.requireNonNull(conditionRoot, "conditionRoot must not be null");
            conditionValueId = validateValueId(conditionValueId, "conditionValueId");
            trueTargetId = validateNodeId(trueTargetId, "trueTargetId");
            falseTargetId = validateNodeId(falseTargetId, "falseTargetId");
        }
    }

    /// Terminal node that ends frontend control flow for the current function.
    public record StopNode(
            @NotNull String id,
            @Nullable String returnValueIdOrNull
    ) implements NodeDef {
        public StopNode {
            id = validateNodeId(id, "id");
            returnValueIdOrNull = validateOptionalValueId(returnValueIdOrNull, "returnValueIdOrNull");
        }
    }

    /// Non-executing source anchor kept only for diagnostics and lexical-position bookkeeping.
    ///
    /// This item intentionally carries no operands or result value id. Real execution semantics such
    /// as declaration commits must use a concrete `ValueOpItem` instead of hiding work behind a
    /// generic statement passthrough.
    public record SourceAnchorItem(@NotNull Statement statement) implements SequenceItem {
        public SourceAnchorItem {
            Objects.requireNonNull(statement, "statement must not be null");
        }

        @Override
        public @NotNull Node anchor() {
            return statement;
        }
    }

    /// Transitional opaque expression evaluation.
    ///
    /// Stage 3 stops hiding execution under `StatementItem`, but stage 5 has not yet exploded every
    /// nested call/member/subscript subtree into dedicated ops. This item therefore represents one
    /// whole source expression that still lowers as a single value-producing step.
    public record OpaqueExprValueItem(
            @NotNull Expression expression,
            @NotNull String resultValueId
    ) implements ValueOpItem {
        public OpaqueExprValueItem {
            Objects.requireNonNull(expression, "expression must not be null");
            resultValueId = validateValueId(resultValueId, "resultValueId");
        }

        @Override
        public @NotNull Node anchor() {
            return expression;
        }

        @Override
        public @NotNull String resultValueIdOrNull() {
            return resultValueId;
        }

        @Override
        public @NotNull List<String> operandValueIds() {
            return List.of();
        }
    }

    /// Local declaration commit that consumes an already-evaluated initializer value when present.
    ///
    /// Keeping the declaration commit explicit prevents later lowering from having to rediscover
    /// whether a nearby expression evaluation belonged to this declaration or to some unrelated
    /// neighboring statement.
    public record LocalDeclarationItem(
            @NotNull VariableDeclaration declaration,
            @Nullable String initializerValueIdOrNull
    ) implements ValueOpItem {
        public LocalDeclarationItem {
            Objects.requireNonNull(declaration, "declaration must not be null");
            initializerValueIdOrNull = validateOptionalValueId(initializerValueIdOrNull, "initializerValueIdOrNull");
        }

        @Override
        public @NotNull Node anchor() {
            return declaration;
        }

        @Override
        public @Nullable String resultValueIdOrNull() {
            return null;
        }

        @Override
        public @NotNull List<String> operandValueIds() {
            return initializerValueIdOrNull == null ? List.of() : List.of(initializerValueIdOrNull);
        }
    }

    /// Future assignment/store commit placeholder.
    public record AssignmentItem(
            @NotNull AssignmentExpression assignment,
            @NotNull String rhsValueId,
            @Nullable String resultValueIdOrNull
    ) implements ValueOpItem {
        public AssignmentItem {
            Objects.requireNonNull(assignment, "assignment must not be null");
            rhsValueId = validateValueId(rhsValueId, "rhsValueId");
            resultValueIdOrNull = validateOptionalValueId(resultValueIdOrNull, "resultValueIdOrNull");
        }

        @Override
        public @NotNull Node anchor() {
            return assignment;
        }

        @Override
        public @NotNull List<String> operandValueIds() {
            return List.of(rhsValueId);
        }
    }

    /// Future member/property load placeholder.
    public record MemberLoadItem(
            @NotNull Expression expression,
            @NotNull String baseValueId,
            @NotNull String resultValueId
    ) implements ValueOpItem {
        public MemberLoadItem {
            Objects.requireNonNull(expression, "expression must not be null");
            baseValueId = validateValueId(baseValueId, "baseValueId");
            resultValueId = validateValueId(resultValueId, "resultValueId");
        }

        @Override
        public @NotNull Node anchor() {
            return expression;
        }

        @Override
        public @NotNull String resultValueIdOrNull() {
            return resultValueId;
        }

        @Override
        public @NotNull List<String> operandValueIds() {
            return List.of(baseValueId);
        }
    }

    /// Future subscript load placeholder.
    public record SubscriptLoadItem(
            @NotNull Expression expression,
            @NotNull String baseValueId,
            @NotNull List<String> argumentValueIds,
            @NotNull String resultValueId
    ) implements ValueOpItem {
        public SubscriptLoadItem {
            Objects.requireNonNull(expression, "expression must not be null");
            baseValueId = validateValueId(baseValueId, "baseValueId");
            argumentValueIds = copyValueIds(argumentValueIds, "argumentValueIds");
            resultValueId = validateValueId(resultValueId, "resultValueId");
        }

        @Override
        public @NotNull Node anchor() {
            return expression;
        }

        @Override
        public @NotNull String resultValueIdOrNull() {
            return resultValueId;
        }

        @Override
        public @NotNull List<String> operandValueIds() {
            var operands = new ArrayList<String>(1 + argumentValueIds.size());
            operands.add(baseValueId);
            operands.addAll(argumentValueIds);
            return List.copyOf(operands);
        }
    }

    /// Future call placeholder.
    public record CallItem(
            @NotNull Expression expression,
            @Nullable String receiverValueIdOrNull,
            @NotNull List<String> argumentValueIds,
            @NotNull String resultValueId
    ) implements ValueOpItem {
        public CallItem {
            Objects.requireNonNull(expression, "expression must not be null");
            receiverValueIdOrNull = validateOptionalValueId(receiverValueIdOrNull, "receiverValueIdOrNull");
            argumentValueIds = copyValueIds(argumentValueIds, "argumentValueIds");
            resultValueId = validateValueId(resultValueId, "resultValueId");
        }

        @Override
        public @NotNull Node anchor() {
            return expression;
        }

        @Override
        public @NotNull String resultValueIdOrNull() {
            return resultValueId;
        }

        @Override
        public @NotNull List<String> operandValueIds() {
            if (receiverValueIdOrNull == null) {
                return argumentValueIds;
            }
            var operands = new ArrayList<String>(1 + argumentValueIds.size());
            operands.add(receiverValueIdOrNull);
            operands.addAll(argumentValueIds);
            return List.copyOf(operands);
        }
    }

    /// Future cast placeholder.
    public record CastItem(
            @NotNull CastExpression expression,
            @NotNull String operandValueId,
            @NotNull String resultValueId
    ) implements ValueOpItem {
        public CastItem {
            Objects.requireNonNull(expression, "expression must not be null");
            operandValueId = validateValueId(operandValueId, "operandValueId");
            resultValueId = validateValueId(resultValueId, "resultValueId");
        }

        @Override
        public @NotNull Node anchor() {
            return expression;
        }

        @Override
        public @NotNull String resultValueIdOrNull() {
            return resultValueId;
        }

        @Override
        public @NotNull List<String> operandValueIds() {
            return List.of(operandValueId);
        }
    }

    /// Future type-test placeholder.
    public record TypeTestItem(
            @NotNull TypeTestExpression expression,
            @NotNull String operandValueId,
            @NotNull String resultValueId
    ) implements ValueOpItem {
        public TypeTestItem {
            Objects.requireNonNull(expression, "expression must not be null");
            operandValueId = validateValueId(operandValueId, "operandValueId");
            resultValueId = validateValueId(resultValueId, "resultValueId");
        }

        @Override
        public @NotNull Node anchor() {
            return expression;
        }

        @Override
        public @NotNull String resultValueIdOrNull() {
            return resultValueId;
        }

        @Override
        public @NotNull List<String> operandValueIds() {
            return List.of(operandValueId);
        }
    }

    private static @NotNull Map<String, NodeDef> copyNodes(@NotNull Map<String, NodeDef> nodes) {
        Objects.requireNonNull(nodes, "nodes must not be null");
        var copiedNodes = new LinkedHashMap<String, NodeDef>(nodes.size());
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

    private static void validateEntryNode(@NotNull Map<String, NodeDef> nodes, @NotNull String entryNodeId) {
        if (!nodes.containsKey(entryNodeId)) {
            throw new IllegalArgumentException("Frontend CFG entry node does not exist: " + entryNodeId);
        }
    }

    private static void validateSuccessorTargets(@NotNull Map<String, NodeDef> nodes) {
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
            @NotNull Map<String, NodeDef> nodes,
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

    static @NotNull String validateValueId(@Nullable String id, @NotNull String fieldName) {
        return validateNodeId(id, fieldName);
    }

    private static @Nullable String validateOptionalValueId(@Nullable String id, @NotNull String fieldName) {
        return id == null ? null : validateValueId(id, fieldName);
    }

    private static @NotNull List<String> copyValueIds(@Nullable List<String> ids, @NotNull String fieldName) {
        var source = Objects.requireNonNull(ids, fieldName + " must not be null");
        var copied = new ArrayList<String>(source.size());
        for (var index = 0; index < source.size(); index++) {
            copied.add(validateValueId(source.get(index), fieldName + "[" + index + "]"));
        }
        return List.copyOf(copied);
    }
}
