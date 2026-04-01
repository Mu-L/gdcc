package dev.superice.gdcc.frontend.lowering.cfg;

import dev.superice.gdcc.enums.GodotOperator;
import dev.superice.gdcc.frontend.lowering.cfg.item.AssignmentItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.CallItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.CastItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.LocalDeclarationItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.MemberLoadItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.OpaqueExprValueItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.SequenceItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.SourceAnchorItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.SubscriptLoadItem;
import dev.superice.gdcc.frontend.lowering.cfg.item.TypeTestItem;
import dev.superice.gdcc.frontend.lowering.cfg.region.FrontendCfgRegion;
import dev.superice.gdcc.frontend.lowering.cfg.region.FrontendElifRegion;
import dev.superice.gdcc.frontend.lowering.cfg.region.FrontendIfRegion;
import dev.superice.gdcc.frontend.lowering.cfg.region.FrontendWhileRegion;
import dev.superice.gdcc.frontend.sema.FrontendAnalysisData;
import dev.superice.gdcc.frontend.sema.FrontendAstSideTable;
import dev.superice.gdcc.frontend.sema.FrontendCallResolutionStatus;
import dev.superice.gdcc.frontend.sema.FrontendExpressionTypeStatus;
import dev.superice.gdcc.frontend.sema.FrontendMemberResolutionStatus;
import dev.superice.gdparser.frontend.ast.AssignmentExpression;
import dev.superice.gdparser.frontend.ast.AttributeCallStep;
import dev.superice.gdparser.frontend.ast.AttributeExpression;
import dev.superice.gdparser.frontend.ast.AttributePropertyStep;
import dev.superice.gdparser.frontend.ast.AttributeStep;
import dev.superice.gdparser.frontend.ast.AttributeSubscriptStep;
import dev.superice.gdparser.frontend.ast.BinaryExpression;
import dev.superice.gdparser.frontend.ast.Block;
import dev.superice.gdparser.frontend.ast.BreakStatement;
import dev.superice.gdparser.frontend.ast.CallExpression;
import dev.superice.gdparser.frontend.ast.CastExpression;
import dev.superice.gdparser.frontend.ast.ContinueStatement;
import dev.superice.gdparser.frontend.ast.DeclarationKind;
import dev.superice.gdparser.frontend.ast.ElifClause;
import dev.superice.gdparser.frontend.ast.Expression;
import dev.superice.gdparser.frontend.ast.ExpressionStatement;
import dev.superice.gdparser.frontend.ast.IdentifierExpression;
import dev.superice.gdparser.frontend.ast.IfStatement;
import dev.superice.gdparser.frontend.ast.LiteralExpression;
import dev.superice.gdparser.frontend.ast.Node;
import dev.superice.gdparser.frontend.ast.PassStatement;
import dev.superice.gdparser.frontend.ast.ReturnStatement;
import dev.superice.gdparser.frontend.ast.SelfExpression;
import dev.superice.gdparser.frontend.ast.Statement;
import dev.superice.gdparser.frontend.ast.SubscriptExpression;
import dev.superice.gdparser.frontend.ast.TypeTestExpression;
import dev.superice.gdparser.frontend.ast.UnaryExpression;
import dev.superice.gdparser.frontend.ast.VariableDeclaration;
import dev.superice.gdparser.frontend.ast.WhileStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/// Frontend CFG builder for one compile-ready executable body.
///
/// The graph stays frontend-only:
/// - `SequenceNode` holds explicit source-level value-op items
/// - `BranchNode` keeps the original condition root and published condition value id
/// - `StopNode` marks function termination or a synthetic fully-terminated merge anchor
///
/// This builder now owns structured executable control flow for the current MVP statement surface:
/// - straight-line statements and local `var`
/// - `if` / `elif` / `else`
/// - `while`
/// - loop-local `break` / `continue`
///
/// Short-circuit `and` / `or` remain outside this step. They still fail fast here so compile-only
/// blockers cannot silently collapse them back into eager binary value ops.
public final class FrontendCfgGraphBuilder {
    private @Nullable FrontendAnalysisData analysisData;
    private @Nullable LinkedHashMap<String, FrontendCfgGraph.NodeDef> nodes;
    private @Nullable FrontendAstSideTable<FrontendCfgRegion> regions;
    private final @NotNull ArrayDeque<LoopFrame> loopStack = new ArrayDeque<>();
    private int nextSequenceIndex;
    private int nextBranchIndex;
    private int nextStopIndex;
    private int nextValueIndex;

    /// Builds one executable-body frontend CFG graph plus every AST-keyed region published inside it.
    ///
    /// The builder consumes only compile-ready frontend facts. Reaching an unsupported statement or a
    /// missing lowering-ready side-table entry here therefore indicates a pipeline contract violation,
    /// and the builder fails fast instead of publishing a partial graph.
    public @NotNull ExecutableBodyBuild buildExecutableBody(
            @NotNull Block rootBlock,
            @NotNull FrontendAnalysisData analysisData
    ) {
        Objects.requireNonNull(rootBlock, "rootBlock must not be null");
        this.analysisData = Objects.requireNonNull(analysisData, "analysisData must not be null");
        nodes = new LinkedHashMap<>();
        regions = new FrontendAstSideTable<>();
        loopStack.clear();
        nextSequenceIndex = 0;
        nextBranchIndex = 0;
        nextStopIndex = 0;
        nextValueIndex = 0;

        var fallthroughStopId = publishStopNode(null);
        var rootBuild = buildBlock(rootBlock, fallthroughStopId);
        var entryId = rootBuild.entryId();
        if (entryId.equals(fallthroughStopId)) {
            entryId = publishSequenceNode(List.of(), fallthroughStopId);
            requireRegions().put(rootBlock, new FrontendCfgRegion.BlockRegion(entryId));
        } else if (!isNodeReferenced(fallthroughStopId)) {
            requireNodes().remove(fallthroughStopId);
        }

        return new ExecutableBodyBuild(
                new FrontendCfgGraph(entryId, orderNodes(entryId)),
                copyRegions(requireRegions())
        );
    }

    private @NotNull BlockBuild buildBlock(@NotNull Block block, @NotNull String continuationId) {
        var state = new BlockState();
        for (var statement : block.statements()) {
            if (!state.reachable()) {
                break;
            }
            processStatement(state, statement);
        }

        var entryId = finalizeBlockState(state, continuationId);
        requireRegions().put(block, new FrontendCfgRegion.BlockRegion(entryId));
        return new BlockBuild(entryId, state.reachable());
    }

    private void processStatement(@NotNull BlockState state, @NotNull Statement statement) {
        switch (statement) {
            case PassStatement passStatement ->
                    requireCurrentSequence(state).items().add(new SourceAnchorItem(passStatement));
            case ExpressionStatement expressionStatement -> buildExpressionStatement(
                    requireCurrentSequence(state).items(),
                    expressionStatement.expression()
            );
            case VariableDeclaration variableDeclaration when variableDeclaration.kind() == DeclarationKind.VAR ->
                    buildLocalDeclaration(requireCurrentSequence(state).items(), variableDeclaration);
            case ReturnStatement returnStatement -> processReturnStatement(state, returnStatement);
            case IfStatement ifStatement -> processIfStatement(state, ifStatement);
            case WhileStatement whileStatement -> processWhileStatement(state, whileStatement);
            case BreakStatement breakStatement ->
                    processLoopJump(state, breakStatement, requireLoopFrame().breakTargetId());
            case ContinueStatement continueStatement -> processLoopJump(
                    state,
                    continueStatement,
                    requireLoopFrame().continueTargetId()
            );
            default -> throw unsupportedReachableStatement(statement);
        }
    }

    private void buildLocalDeclaration(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull VariableDeclaration variableDeclaration
    ) {
        var initializer = variableDeclaration.value();
        var initializerValueId = initializer == null
                ? null
                : buildValue(items, initializer, nextVariableValueId(variableDeclaration.name()));
        items.add(new LocalDeclarationItem(variableDeclaration, initializerValueId));
    }

    private void buildExpressionStatement(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull Expression expression
    ) {
        requireLoweringReadyExpressionType(expression);
        switch (expression) {
            case AssignmentExpression assignmentExpression -> buildAssignmentCommit(items, assignmentExpression);
            default -> buildValue(items, expression, null);
        }
    }

    private void processReturnStatement(@NotNull BlockState state, @NotNull ReturnStatement returnStatement) {
        var sequence = requireCurrentSequence(state);
        var returnValue = returnStatement.value();
        var returnValueId = returnValue == null ? null : buildValue(sequence.items(), returnValue, null);
        closeCurrentSequence(state, publishStopNode(returnValueId));
        state.setReachable(false);
    }

    private void processLoopJump(@NotNull BlockState state, @NotNull Statement statement, @NotNull String targetId) {
        var sequence = requireCurrentSequence(state);
        sequence.items().add(new SourceAnchorItem(statement));
        closeCurrentSequence(state, targetId);
        state.setReachable(false);
    }

    private void processIfStatement(@NotNull BlockState state, @NotNull IfStatement ifStatement) {
        var mergeSequence = new OpenSequence(nextSequenceId());
        var thenBuild = buildBlock(ifStatement.body(), mergeSequence.id());
        var falseBuild = buildIfFalseChain(ifStatement.elifClauses(), ifStatement.elseBody(), mergeSequence.id());
        var conditionBuild = buildCondition(ifStatement.condition(), thenBuild.entryId(), falseBuild.entryId());
        attachStructuredEntry(state, conditionBuild.entryId());

        var fallsThrough = thenBuild.fallsThrough() || falseBuild.fallsThrough();
        var mergeId = fallsThrough ? mergeSequence.id() : publishStopNode(null);
        requireRegions().put(
                ifStatement,
                new FrontendIfRegion(
                        conditionBuild.entryId(),
                        thenBuild.entryId(),
                        falseBuild.entryId(),
                        mergeId
                )
        );

        if (fallsThrough) {
            state.setCurrentSequence(mergeSequence);
            state.setReachable(true);
            return;
        }
        state.setCurrentSequence(null);
        state.setReachable(false);
    }

    private @NotNull ClauseBuild buildIfFalseChain(
            @NotNull List<ElifClause> elifClauses,
            @Nullable Block elseBody,
            @NotNull String mergeTargetId
    ) {
        if (!elifClauses.isEmpty()) {
            return buildElifChain(elifClauses, 0, elseBody, mergeTargetId);
        }
        if (elseBody != null) {
            var elseBuild = buildBlock(elseBody, mergeTargetId);
            return new ClauseBuild(elseBuild.entryId(), elseBuild.fallsThrough());
        }
        return new ClauseBuild(mergeTargetId, true);
    }

    private @NotNull ClauseBuild buildElifChain(
            @NotNull List<ElifClause> elifClauses,
            int clauseIndex,
            @Nullable Block elseBody,
            @NotNull String mergeTargetId
    ) {
        if (clauseIndex >= elifClauses.size()) {
            if (elseBody != null) {
                var elseBuild = buildBlock(elseBody, mergeTargetId);
                return new ClauseBuild(elseBuild.entryId(), elseBuild.fallsThrough());
            }
            return new ClauseBuild(mergeTargetId, true);
        }

        var elifClause = elifClauses.get(clauseIndex);
        var nextClause = buildElifChain(elifClauses, clauseIndex + 1, elseBody, mergeTargetId);
        var bodyBuild = buildBlock(elifClause.body(), mergeTargetId);
        var conditionBuild = buildCondition(elifClause.condition(), bodyBuild.entryId(), nextClause.entryId());
        requireRegions().put(
                elifClause,
                new FrontendElifRegion(
                        conditionBuild.entryId(),
                        bodyBuild.entryId(),
                        nextClause.entryId()
                )
        );
        return new ClauseBuild(conditionBuild.entryId(), bodyBuild.fallsThrough() || nextClause.fallsThrough());
    }

    private void processWhileStatement(@NotNull BlockState state, @NotNull WhileStatement whileStatement) {
        var exitSequence = new OpenSequence(nextSequenceId());
        var conditionSequence = new OpenSequence(nextSequenceId());
        loopStack.push(new LoopFrame(conditionSequence.id(), exitSequence.id()));
        BlockBuild bodyBuild;
        try {
            bodyBuild = buildBlock(whileStatement.body(), conditionSequence.id());
        } finally {
            loopStack.pop();
        }

        var conditionBuild = buildConditionIntoSequence(
                conditionSequence,
                whileStatement.condition(),
                bodyBuild.entryId(),
                exitSequence.id()
        );
        attachStructuredEntry(state, conditionBuild.entryId());
        requireRegions().put(
                whileStatement,
                new FrontendWhileRegion(
                        conditionBuild.entryId(),
                        bodyBuild.entryId(),
                        exitSequence.id()
                )
        );
        state.setCurrentSequence(exitSequence);
        state.setReachable(true);
    }

    private void attachStructuredEntry(@NotNull BlockState state, @NotNull String structuredEntryId) {
        if (state.currentSequenceOrNull() == null) {
            state.setEntryIdIfMissing(structuredEntryId);
            return;
        }
        closeCurrentSequence(state, structuredEntryId);
    }

    private @NotNull ConditionBuild buildCondition(
            @NotNull Expression condition,
            @NotNull String trueTargetId,
            @NotNull String falseTargetId
    ) {
        return buildConditionIntoSequence(new OpenSequence(nextSequenceId()), condition, trueTargetId, falseTargetId);
    }

    private @NotNull ConditionBuild buildConditionIntoSequence(
            @NotNull OpenSequence conditionSequence,
            @NotNull Expression condition,
            @NotNull String trueTargetId,
            @NotNull String falseTargetId
    ) {
        var conditionValueId = buildValue(conditionSequence.items(), condition, null);
        var branchId = nextBranchId();
        publishSequenceNode(conditionSequence.id(), conditionSequence.items(), branchId);
        requireNodes().put(
                branchId,
                new FrontendCfgGraph.BranchNode(branchId, condition, conditionValueId, trueTargetId, falseTargetId)
        );
        return new ConditionBuild(conditionSequence.id(), branchId);
    }

    /// Recursively materializes one lowering-ready value and returns the published frontend-local id.
    ///
    /// The builder deliberately special-cases only operations whose semantics later lowering must not
    /// rediscover from raw AST alone. Generic expressions still use `OpaqueExprValueItem`, but only
    /// after all of their lowering-ready children have already published explicit operand ids.
    /// Short-circuit `and` / `or` no longer share that generic eager route; they are intercepted by
    /// a dedicated unimplemented path so compile-blocked sources cannot accidentally regress into one
    /// linear binary item.
    private @NotNull String buildValue(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull Expression expression,
            @Nullable String preferredResultValueId
    ) {
        requireLoweringReadyExpressionType(expression);
        return switch (expression) {
            case AssignmentExpression _ -> throw new IllegalStateException(
                    "Assignment expressions do not produce a lowering-ready value in the current compile surface"
            );
            case AttributeExpression attributeExpression -> buildAttributeExpressionValue(
                    items,
                    attributeExpression,
                    preferredResultValueId
            );
            case CallExpression callExpression -> buildBareCallValue(items, callExpression, preferredResultValueId);
            case SubscriptExpression subscriptExpression -> buildPlainSubscriptValue(
                    items,
                    subscriptExpression,
                    preferredResultValueId
            );
            case CastExpression castExpression -> buildCastValue(items, castExpression, preferredResultValueId);
            case TypeTestExpression typeTestExpression ->
                    buildTypeTestValue(items, typeTestExpression, preferredResultValueId);
            case UnaryExpression unaryExpression -> emitOpaqueValue(
                    items,
                    unaryExpression,
                    List.of(buildValue(items, unaryExpression.operand(), null)),
                    preferredResultValueId
            );
            case BinaryExpression binaryExpression when isShortCircuitBinaryExpression(binaryExpression) ->
                    buildShortCircuitBinaryValue(binaryExpression);
            case BinaryExpression binaryExpression -> emitOpaqueValue(
                    items,
                    binaryExpression,
                    List.of(
                            buildValue(items, binaryExpression.left(), null),
                            buildValue(items, binaryExpression.right(), null)
                    ),
                    preferredResultValueId
            );
            case IdentifierExpression _, LiteralExpression _, SelfExpression _ -> emitOpaqueValue(
                    items,
                    expression,
                    List.of(),
                    preferredResultValueId
            );
            default -> throw unsupportedReachableExpression(expression);
        };
    }

    /// Attribute chains are expanded step by step so later lowering receives explicit intermediate
    /// value ids instead of having to rerun chain reduction over the full outer expression root.
    private @NotNull String buildAttributeExpressionValue(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull AttributeExpression attributeExpression,
            @Nullable String preferredResultValueId
    ) {
        if (attributeExpression.steps().isEmpty()) {
            throw new IllegalStateException("AttributeExpression must contain at least one step");
        }
        var currentValueId = buildValue(items, attributeExpression.base(), null);
        for (var stepIndex = 0; stepIndex < attributeExpression.steps().size(); stepIndex++) {
            var step = attributeExpression.steps().get(stepIndex);
            currentValueId = applyAttributeStep(
                    items,
                    currentValueId,
                    step,
                    stepIndex + 1 == attributeExpression.steps().size() ? preferredResultValueId : null
            );
        }
        return currentValueId;
    }

    /// Bare call lowering consumes the published `resolvedCalls()` fact directly.
    ///
    /// The current compile-ready contract only permits bare/global/static calls here. Callable-value
    /// invocation remains a separate future route, so a non-identifier callee is treated as a
    /// protocol violation instead of being silently dropped from operand ordering.
    private @NotNull String buildBareCallValue(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull CallExpression callExpression,
            @Nullable String preferredResultValueId
    ) {
        if (!(callExpression.callee() instanceof IdentifierExpression)) {
            throw new IllegalStateException(
                    "Bare call lowering currently requires an IdentifierExpression callee, but got "
                            + callExpression.callee().getClass().getSimpleName()
            );
        }
        var publishedCall = requireLoweringReadyCall(callExpression);
        var argumentValueIds = buildArgumentValues(items, callExpression.arguments());
        var resultValueId = chooseResultValueId(preferredResultValueId);
        items.add(new CallItem(
                callExpression,
                publishedCall.callableName(),
                null,
                argumentValueIds,
                resultValueId
        ));
        return resultValueId;
    }

    /// Plain subscripts first materialize their base and arguments, then commit one explicit indexed
    /// read item that consumes those operand ids.
    private @NotNull String buildPlainSubscriptValue(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull SubscriptExpression subscriptExpression,
            @Nullable String preferredResultValueId
    ) {
        var baseValueId = buildValue(items, subscriptExpression.base(), null);
        var argumentValueIds = buildArgumentValues(items, subscriptExpression.arguments());
        var resultValueId = chooseResultValueId(preferredResultValueId);
        items.add(new SubscriptLoadItem(
                subscriptExpression,
                null,
                baseValueId,
                argumentValueIds,
                resultValueId
        ));
        return resultValueId;
    }

    /// Cast expressions are still compile-blocked by the default pipeline, but the item contract is
    /// already wired so targeted builder tests and later compile-surface expansion do not require a
    /// structural rewrite of the frontend CFG surface.
    private @NotNull String buildCastValue(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull CastExpression castExpression,
            @Nullable String preferredResultValueId
    ) {
        var operandValueId = buildValue(items, castExpression.value(), null);
        var resultValueId = chooseResultValueId(preferredResultValueId);
        items.add(new CastItem(
                castExpression,
                operandValueId,
                resultValueId
        ));
        return resultValueId;
    }

    /// Type-test expressions share the same “child first, then one explicit result item” contract as
    /// casts. They remain compile-blocked today, but the CFG item surface is ready for that future
    /// migration.
    private @NotNull String buildTypeTestValue(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull TypeTestExpression typeTestExpression,
            @Nullable String preferredResultValueId
    ) {
        var operandValueId = buildValue(items, typeTestExpression.value(), null);
        var resultValueId = chooseResultValueId(preferredResultValueId);
        items.add(new TypeTestItem(
                typeTestExpression,
                operandValueId,
                resultValueId
        ));
        return resultValueId;
    }

    /// `and` / `or` never belong to the generic eager binary route.
    ///
    /// Their final lowering must allocate a shared result slot, branch on the left operand, and only
    /// materialize the right operand on the non-short-circuit path. Reaching this helper therefore
    /// means the compile gate was bypassed and we must fail before any child operand is eagerly
    /// lowered.
    private @NotNull String buildShortCircuitBinaryValue(@NotNull BinaryExpression binaryExpression) {
        throw new IllegalStateException(
                "Binary operator '"
                        + binaryExpression.operator()
                        + "' must use the dedicated frontend CFG short-circuit path, but that path is not implemented yet"
        );
    }

    /// Assignment commit preserves target evaluation before RHS evaluation.
    ///
    /// The returned operand list on `AssignmentItem` therefore starts with any already-evaluated
    /// target receiver/index operands, then appends the RHS value id as the final consumed operand.
    private void buildAssignmentCommit(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull AssignmentExpression assignmentExpression
    ) {
        var targetOperandValueIds = buildAssignmentTargetOperands(items, assignmentExpression.left());
        var rhsValueId = buildValue(items, assignmentExpression.right(), null);
        items.add(new AssignmentItem(
                assignmentExpression,
                targetOperandValueIds,
                rhsValueId,
                null
        ));
    }

    /// Builds the already-evaluated operands required to commit one assignment target.
    ///
    /// The target AST itself remains on `AssignmentItem`, but any child expressions with real
    /// evaluation order, such as chain prefixes or subscript arguments, are materialized here so
    /// later lowering does not need to recurse back into the target subtree to discover them.
    private @NotNull List<String> buildAssignmentTargetOperands(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull Expression targetExpression
    ) {
        return switch (targetExpression) {
            case IdentifierExpression _ -> List.of();
            case AttributeExpression attributeExpression -> buildAttributeTargetOperands(items, attributeExpression);
            case SubscriptExpression subscriptExpression -> {
                var operands = new ArrayList<String>(1 + subscriptExpression.arguments().size());
                operands.add(buildAssignmentTargetValue(items, subscriptExpression.base()));
                operands.addAll(buildArgumentValues(items, subscriptExpression.arguments()));
                yield List.copyOf(operands);
            }
            default -> throw unsupportedReachableAssignmentTarget(targetExpression);
        };
    }

    /// Attribute assignment targets lower every prefix step as an ordinary read/call/subscript chain,
    /// then stop before the final writable slot.
    ///
    /// For example:
    /// - `obj.payload = rhs` publishes only the receiver value for `obj`
    /// - `obj.items[i] = rhs` publishes the receiver value for `obj` plus the index operand ids
    /// - `obj.a().items[i] = rhs` first builds `obj.a()` as explicit prefix value-ops, then exports
    ///   the final target receiver/index operands
    private @NotNull List<String> buildAttributeTargetOperands(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull AttributeExpression attributeExpression
    ) {
        if (attributeExpression.steps().isEmpty()) {
            throw new IllegalStateException("AttributeExpression assignment target must contain at least one step");
        }

        var currentValueId = buildAssignmentTargetValue(items, attributeExpression.base());
        for (var stepIndex = 0; stepIndex + 1 < attributeExpression.steps().size(); stepIndex++) {
            currentValueId = applyAttributeStep(items, currentValueId, attributeExpression.steps().get(stepIndex), null);
        }

        var finalStep = attributeExpression.steps().getLast();
        return switch (finalStep) {
            case AttributePropertyStep _ -> List.of(currentValueId);
            case AttributeSubscriptStep attributeSubscriptStep -> {
                var operands = new ArrayList<String>(1 + attributeSubscriptStep.arguments().size());
                operands.add(currentValueId);
                operands.addAll(buildArgumentValues(items, attributeSubscriptStep.arguments()));
                yield List.copyOf(operands);
            }
            default -> throw new IllegalStateException(
                    "Assignment target step '"
                            + finalStep.getClass().getSimpleName()
                            + "' is not supported by the current frontend CFG contract"
            );
        };
    }

    /// Assignment-target prefixes are not always part of the ordinary published `expressionTypes()`
    /// surface.
    ///
    /// For example, the container base of `items[idx] = rhs` may only be visited through assignment
    /// target analysis, so the builder cannot require a normal expression-type entry before it
    /// materializes the receiver value. This helper therefore falls back to a target-specific value
    /// path for those prefixes while still reusing ordinary `buildValue(...)` whenever a lowering-ready
    /// expression fact actually exists.
    private @NotNull String buildAssignmentTargetValue(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull Expression expression
    ) {
        if (hasLoweringReadyExpressionType(expression)) {
            return buildValue(items, expression, null);
        }
        return switch (expression) {
            case IdentifierExpression _, SelfExpression _ -> emitOpaqueValue(items, expression, List.of(), null);
            case AttributeExpression attributeExpression ->
                    buildAssignmentTargetAttributeValue(items, attributeExpression);
            case SubscriptExpression subscriptExpression ->
                    buildAssignmentTargetSubscriptValue(items, subscriptExpression);
            case CallExpression callExpression -> buildBareCallValue(items, callExpression, null);
            default -> throw new IllegalStateException(
                    "Assignment target value "
                            + expression.getClass().getSimpleName()
                            + " is missing a lowering-ready expression fact"
            );
        };
    }

    private @NotNull String buildAssignmentTargetAttributeValue(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull AttributeExpression attributeExpression
    ) {
        if (attributeExpression.steps().isEmpty()) {
            throw new IllegalStateException("AttributeExpression target value must contain at least one step");
        }
        var currentValueId = buildAssignmentTargetValue(items, attributeExpression.base());
        for (var step : attributeExpression.steps()) {
            currentValueId = applyAttributeStep(items, currentValueId, step, null);
        }
        return currentValueId;
    }

    private @NotNull String buildAssignmentTargetSubscriptValue(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull SubscriptExpression subscriptExpression
    ) {
        var baseValueId = buildAssignmentTargetValue(items, subscriptExpression.base());
        var argumentValueIds = buildArgumentValues(items, subscriptExpression.arguments());
        var resultValueId = chooseResultValueId(null);
        items.add(new SubscriptLoadItem(
                subscriptExpression,
                null,
                baseValueId,
                argumentValueIds,
                resultValueId
        ));
        return resultValueId;
    }

    /// Applies one attribute-chain step to the current receiver value and returns the produced value id.
    ///
    /// The step kind decides which explicit item is emitted, but the overall contract stays uniform:
    /// the receiver value id arrives from the previous chain segment, step-local arguments are built
    /// before the item is appended, and the returned value id becomes the receiver for the next step.
    private @NotNull String applyAttributeStep(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull String receiverValueId,
            @NotNull AttributeStep step,
            @Nullable String preferredResultValueId
    ) {
        return switch (step) {
            case AttributePropertyStep attributePropertyStep -> {
                var publishedMember = requireLoweringReadyMember(attributePropertyStep);
                var resultValueId = chooseResultValueId(preferredResultValueId);
                items.add(new MemberLoadItem(
                        attributePropertyStep,
                        publishedMember.memberName(),
                        receiverValueId,
                        resultValueId
                ));
                yield resultValueId;
            }
            case AttributeCallStep attributeCallStep -> {
                var publishedCall = requireLoweringReadyCall(attributeCallStep);
                var argumentValueIds = buildArgumentValues(items, attributeCallStep.arguments());
                var resultValueId = chooseResultValueId(preferredResultValueId);
                items.add(new CallItem(
                        attributeCallStep,
                        publishedCall.callableName(),
                        receiverValueId,
                        argumentValueIds,
                        resultValueId
                ));
                yield resultValueId;
            }
            case AttributeSubscriptStep attributeSubscriptStep -> {
                var argumentValueIds = buildArgumentValues(items, attributeSubscriptStep.arguments());
                var resultValueId = chooseResultValueId(preferredResultValueId);
                items.add(new SubscriptLoadItem(
                        attributeSubscriptStep,
                        attributeSubscriptStep.name(),
                        receiverValueId,
                        argumentValueIds,
                        resultValueId
                ));
                yield resultValueId;
            }
            default -> throw new IllegalStateException(
                    "Unsupported attribute step in frontend CFG builder: " + step.getClass().getSimpleName()
            );
        };
    }

    private @NotNull List<String> buildArgumentValues(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull List<Expression> arguments
    ) {
        var valueIds = new ArrayList<String>(arguments.size());
        for (var argument : arguments) {
            valueIds.add(buildValue(items, argument, null));
        }
        return List.copyOf(valueIds);
    }

    /// Generic opaque items still exist as a bridge for simple expression forms whose exact lowering
    /// will be finalized later, but they no longer hide nested child evaluation order.
    private @NotNull String emitOpaqueValue(
            @NotNull ArrayList<SequenceItem> items,
            @NotNull Expression expression,
            @NotNull List<String> operandValueIds,
            @Nullable String preferredResultValueId
    ) {
        var resultValueId = chooseResultValueId(preferredResultValueId);
        items.add(new OpaqueExprValueItem(expression, operandValueIds, resultValueId));
        return resultValueId;
    }

    private @NotNull String finalizeBlockState(@NotNull BlockState state, @NotNull String continuationId) {
        if (state.reachable()) {
            closeCurrentSequence(state, continuationId);
            return state.entryIdOrNull() == null ? continuationId : state.entryIdOrNull();
        }
        var entryId = state.entryIdOrNull();
        if (entryId == null) {
            throw new IllegalStateException("Structured block terminated before publishing an entry node");
        }
        return entryId;
    }

    private void closeCurrentSequence(@NotNull BlockState state, @NotNull String nextId) {
        var currentSequence = state.currentSequenceOrNull();
        if (currentSequence == null) {
            return;
        }
        if (!currentSequence.items().isEmpty()) {
            publishSequenceNode(currentSequence.id(), currentSequence.items(), nextId);
        } else if (Objects.equals(state.entryIdOrNull(), currentSequence.id())) {
            state.setEntryId(nextId);
        } else {
            publishSequenceNode(currentSequence.id(), List.of(), nextId);
        }
        state.setCurrentSequence(null);
    }

    private @NotNull OpenSequence requireCurrentSequence(@NotNull BlockState state) {
        var currentSequence = state.currentSequenceOrNull();
        if (currentSequence != null) {
            return currentSequence;
        }
        var created = new OpenSequence(nextSequenceId());
        state.setEntryIdIfMissing(created.id());
        state.setCurrentSequence(created);
        return created;
    }

    private @NotNull LinkedHashMap<String, FrontendCfgGraph.NodeDef> orderNodes(@NotNull String entryId) {
        var ordered = new LinkedHashMap<String, FrontendCfgGraph.NodeDef>();
        var visited = new LinkedHashSet<String>();
        visitNode(entryId, visited, ordered);
        for (var entry : requireNodes().entrySet()) {
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    private void visitNode(
            @NotNull String nodeId,
            @NotNull LinkedHashSet<String> visited,
            @NotNull LinkedHashMap<String, FrontendCfgGraph.NodeDef> ordered
    ) {
        if (!visited.add(nodeId)) {
            return;
        }
        var node = requireNodes().get(nodeId);
        if (node == null) {
            throw new IllegalStateException("Frontend CFG node has not been published: " + nodeId);
        }
        ordered.put(nodeId, node);
        switch (node) {
            case FrontendCfgGraph.SequenceNode(_, _, var nextId) -> visitNode(nextId, visited, ordered);
            case FrontendCfgGraph.BranchNode(_, _, _, var trueTargetId, var falseTargetId) -> {
                visitNode(trueTargetId, visited, ordered);
                visitNode(falseTargetId, visited, ordered);
            }
            case FrontendCfgGraph.StopNode _ -> {
            }
        }
    }

    private boolean isNodeReferenced(@NotNull String nodeId) {
        for (var node : requireNodes().values()) {
            switch (node) {
                case FrontendCfgGraph.SequenceNode(_, _, var nextId) -> {
                    if (nextId.equals(nodeId)) {
                        return true;
                    }
                }
                case FrontendCfgGraph.BranchNode(_, _, _, var trueTargetId, var falseTargetId) -> {
                    if (trueTargetId.equals(nodeId) || falseTargetId.equals(nodeId)) {
                        return true;
                    }
                }
                case FrontendCfgGraph.StopNode _ -> {
                }
            }
        }
        return false;
    }

    private @NotNull String publishSequenceNode(@NotNull List<SequenceItem> items, @NotNull String nextId) {
        var sequenceId = nextSequenceId();
        publishSequenceNode(sequenceId, items, nextId);
        return sequenceId;
    }

    private void publishSequenceNode(
            @NotNull String sequenceId,
            @NotNull List<SequenceItem> items,
            @NotNull String nextId
    ) {
        requireNodes().put(sequenceId, new FrontendCfgGraph.SequenceNode(sequenceId, items, nextId));
    }

    private @NotNull String publishStopNode(@Nullable String returnValueIdOrNull) {
        var stopId = nextStopId();
        requireNodes().put(stopId, new FrontendCfgGraph.StopNode(stopId, returnValueIdOrNull));
        return stopId;
    }

    private @NotNull FrontendAstSideTable<FrontendCfgRegion> requireRegions() {
        if (regions == null) {
            throw new IllegalStateException("Frontend CFG regions have not been initialized");
        }
        return regions;
    }

    private @NotNull LinkedHashMap<String, FrontendCfgGraph.NodeDef> requireNodes() {
        if (nodes == null) {
            throw new IllegalStateException("Frontend CFG nodes have not been initialized");
        }
        return nodes;
    }

    private @NotNull FrontendAnalysisData requireAnalysisData() {
        if (analysisData == null) {
            throw new IllegalStateException("Frontend analysis data has not been initialized");
        }
        return analysisData;
    }

    private @NotNull LoopFrame requireLoopFrame() {
        var loopFrame = loopStack.peek();
        if (loopFrame == null) {
            throw new IllegalStateException("Loop control statement requires an active loop frame");
        }
        return loopFrame;
    }

    /// Sequence ids are lexical-order scoped to one build so tests can assert exact graph shape
    /// without leaking counters across functions.
    private @NotNull String nextSequenceId() {
        return "seq_" + nextSequenceIndex++;
    }

    private @NotNull String nextBranchId() {
        return "branch_" + nextBranchIndex++;
    }

    /// Stop ids share the same per-function deterministic contract as sequence ids.
    private @NotNull String nextStopId() {
        return "stop_" + nextStopIndex++;
    }

    /// Value ids name frontend-local temporary results referenced by later CFG nodes.
    private @NotNull String nextValueId() {
        return "v" + nextValueIndex++;
    }

    /// Variable initializer ids keep the declaration name as a stable prefix while still sharing the
    /// same monotonic counter as other frontend-local values.
    private @NotNull String nextVariableValueId(@NotNull String variableName) {
        return FrontendCfgGraph.validateNodeId(variableName, "variableName") + "_" + nextValueIndex++;
    }

    private @NotNull String chooseResultValueId(@Nullable String preferredResultValueId) {
        return preferredResultValueId == null ? nextValueId() : preferredResultValueId;
    }

    private static boolean isShortCircuitBinaryExpression(@NotNull BinaryExpression binaryExpression) {
        var operator = tryResolveBinaryOperator(binaryExpression.operator());
        return operator == GodotOperator.AND || operator == GodotOperator.OR;
    }

    private static @Nullable GodotOperator tryResolveBinaryOperator(@NotNull String operatorText) {
        try {
            return GodotOperator.fromSourceLexeme(
                    Objects.requireNonNull(operatorText, "operatorText must not be null"),
                    GodotOperator.OperatorArity.BINARY
            );
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    /// Compile-ready lowering must only see expressions whose type facts already stabilized to a
    /// lowering-safe state.
    ///
    /// `BLOCKED`, `DEFERRED`, `FAILED`, and `UNSUPPORTED` all indicate the compile gate should have
    /// stopped the pipeline earlier. The builder therefore treats them as protocol violations instead
    /// of trying to recover locally.
    private boolean hasLoweringReadyExpressionType(@NotNull Expression expression) {
        var publishedType = requireAnalysisData().expressionTypes().get(expression);
        return publishedType != null
                && (publishedType.status() == FrontendExpressionTypeStatus.RESOLVED
                || publishedType.status() == FrontendExpressionTypeStatus.DYNAMIC);
    }

    private void requireLoweringReadyExpressionType(@NotNull Expression expression) {
        var publishedType = requireAnalysisData().expressionTypes().get(expression);
        if (publishedType == null) {
            throw new IllegalStateException(
                    "expressionTypes() is missing a lowering-ready fact for "
                            + expression.getClass().getSimpleName()
                            + " at "
                            + expression.range()
            );
        }
        if (publishedType.status() != FrontendExpressionTypeStatus.RESOLVED
                && publishedType.status() != FrontendExpressionTypeStatus.DYNAMIC) {
            throw new IllegalStateException(
                    "Expression "
                            + expression.getClass().getSimpleName()
                            + " is not lowering-ready: "
                            + publishedType.status()
            );
        }
    }

    private @NotNull dev.superice.gdcc.frontend.sema.FrontendResolvedCall requireLoweringReadyCall(@NotNull Node callAnchor) {
        var publishedCall = requireAnalysisData().resolvedCalls().get(callAnchor);
        if (publishedCall == null) {
            throw new IllegalStateException(
                    "resolvedCalls() is missing a lowering-ready fact for " + callAnchor.getClass().getSimpleName()
            );
        }
        if (publishedCall.status() != FrontendCallResolutionStatus.RESOLVED
                && publishedCall.status() != FrontendCallResolutionStatus.DYNAMIC) {
            throw new IllegalStateException(
                    "Call anchor "
                            + callAnchor.getClass().getSimpleName()
                            + " is not lowering-ready: "
                            + publishedCall.status()
            );
        }
        return publishedCall;
    }

    private @NotNull dev.superice.gdcc.frontend.sema.FrontendResolvedMember requireLoweringReadyMember(
            @NotNull AttributePropertyStep attributePropertyStep
    ) {
        var publishedMember = requireAnalysisData().resolvedMembers().get(attributePropertyStep);
        if (publishedMember == null) {
            throw new IllegalStateException(
                    "resolvedMembers() is missing a lowering-ready fact for AttributePropertyStep '"
                            + attributePropertyStep.name()
                            + "'"
            );
        }
        if (publishedMember.status() != FrontendMemberResolutionStatus.RESOLVED
                && publishedMember.status() != FrontendMemberResolutionStatus.DYNAMIC) {
            throw new IllegalStateException(
                    "AttributePropertyStep '"
                            + attributePropertyStep.name()
                            + "' is not lowering-ready: "
                            + publishedMember.status()
            );
        }
        return publishedMember;
    }

    private static @NotNull IllegalStateException unsupportedReachableStatement(@NotNull Statement statement) {
        return new IllegalStateException(
                "Frontend CFG builder reached an unsupported reachable statement: "
                        + statement.getClass().getSimpleName()
        );
    }

    private static @NotNull IllegalStateException unsupportedReachableExpression(@NotNull Expression expression) {
        return new IllegalStateException(
                "Frontend CFG builder reached an unsupported lowering-ready expression: "
                        + expression.getClass().getSimpleName()
        );
    }

    private static @NotNull IllegalStateException unsupportedReachableAssignmentTarget(@NotNull Expression targetExpression) {
        return new IllegalStateException(
                "Frontend CFG builder reached an unsupported assignment target expression: "
                        + targetExpression.getClass().getSimpleName()
        );
    }

    private static @NotNull FrontendAstSideTable<FrontendCfgRegion> copyRegions(
            @NotNull FrontendAstSideTable<FrontendCfgRegion> regions
    ) {
        var copied = new FrontendAstSideTable<FrontendCfgRegion>();
        copied.putAll(regions);
        return copied;
    }

    public record ExecutableBodyBuild(
            @NotNull FrontendCfgGraph graph,
            @NotNull FrontendAstSideTable<FrontendCfgRegion> regions
    ) {
        public ExecutableBodyBuild {
            Objects.requireNonNull(graph, "graph must not be null");
            regions = copyRegions(Objects.requireNonNull(regions, "regions must not be null"));
        }
    }

    private record ConditionBuild(
            @NotNull String entryId,
            @NotNull String branchId
    ) {
        private ConditionBuild {
            Objects.requireNonNull(entryId, "entryId must not be null");
            Objects.requireNonNull(branchId, "branchId must not be null");
        }
    }

    private record ClauseBuild(
            @NotNull String entryId,
            boolean fallsThrough
    ) {
        private ClauseBuild {
            Objects.requireNonNull(entryId, "entryId must not be null");
        }
    }

    private record BlockBuild(
            @NotNull String entryId,
            boolean fallsThrough
    ) {
        private BlockBuild {
            Objects.requireNonNull(entryId, "entryId must not be null");
        }
    }

    private record OpenSequence(
            @NotNull String id,
            @NotNull ArrayList<SequenceItem> items
    ) {
        private OpenSequence(@NotNull String id) {
            this(id, new ArrayList<>());
        }

        private OpenSequence {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(items, "items must not be null");
        }
    }

    private record LoopFrame(
            @NotNull String continueTargetId,
            @NotNull String breakTargetId
    ) {
        private LoopFrame {
            Objects.requireNonNull(continueTargetId, "continueTargetId must not be null");
            Objects.requireNonNull(breakTargetId, "breakTargetId must not be null");
        }
    }

    private static final class BlockState {
        private @Nullable String entryId;
        private @Nullable OpenSequence currentSequence;
        private boolean reachable = true;

        private @Nullable String entryIdOrNull() {
            return entryId;
        }

        private void setEntryId(@NotNull String entryId) {
            this.entryId = Objects.requireNonNull(entryId, "entryId must not be null");
        }

        private void setEntryIdIfMissing(@NotNull String entryId) {
            if (this.entryId == null) {
                this.entryId = Objects.requireNonNull(entryId, "entryId must not be null");
            }
        }

        private @Nullable OpenSequence currentSequenceOrNull() {
            return currentSequence;
        }

        private void setCurrentSequence(@Nullable OpenSequence currentSequence) {
            this.currentSequence = currentSequence;
        }

        private boolean reachable() {
            return reachable;
        }

        private void setReachable(boolean reachable) {
            this.reachable = reachable;
        }
    }
}
