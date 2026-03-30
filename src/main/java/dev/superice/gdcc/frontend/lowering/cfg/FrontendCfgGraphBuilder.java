package dev.superice.gdcc.frontend.lowering.cfg;

import dev.superice.gdparser.frontend.ast.Block;
import dev.superice.gdparser.frontend.ast.DeclarationKind;
import dev.superice.gdparser.frontend.ast.ExpressionStatement;
import dev.superice.gdparser.frontend.ast.PassStatement;
import dev.superice.gdparser.frontend.ast.ReturnStatement;
import dev.superice.gdparser.frontend.ast.Statement;
import dev.superice.gdparser.frontend.ast.VariableDeclaration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/// Frontend CFG builder for the phase-3 straight-line executable-body subset.
///
/// This is the first concrete producer of the new frontend-only CFG graph, but it still targets a
/// deliberately tiny surface so the migration can land incrementally without forcing the old
/// metadata-only CFG pass to disappear in the same change.
///
/// The accepted source shape is limited to executable bodies whose control flow is fully linear:
/// - empty blocks
/// - `pass`
/// - `ExpressionStatement`
/// - local `var`
/// - `ReturnStatement`
///
/// A successful build currently always produces exactly:
/// - one entry `SequenceNode` that stores all linear work items in lexical order
/// - one terminal `StopNode` that closes the function path
///
/// Structured statements are still migrated in later phases. The default lowering pipeline therefore
/// uses `supportsStraightLineExecutableBody(...)` as a preflight gate so legacy metadata publication
/// can continue handling those functions until the structured graph work lands.
public final class FrontendCfgGraphBuilder {
    private int nextSequenceIndex;
    private int nextStopIndex;
    private int nextValueIndex;

    /// Cheap preflight for the default pipeline.
    ///
    /// The build pass uses this helper to decide whether the new phase-3 builder can fully own the
    /// current executable body, or whether the function must temporarily stay on the legacy
    /// metadata-only path.
    ///
    /// The scan intentionally stops at the first reachable `return`. Anything after that point is
    /// lexical remainder and must not veto the phase-3 straight-line shape, because this builder
    /// never attempts to materialize unreachable siblings.
    public static boolean supportsStraightLineExecutableBody(@NotNull Block rootBlock) {
        Objects.requireNonNull(rootBlock, "rootBlock must not be null");
        for (var statement : rootBlock.statements()) {
            if (!isSupportedStraightLineStatement(statement)) {
                return false;
            }
            if (statement instanceof ReturnStatement) {
                return true;
            }
        }
        return true;
    }

    /// Builds the phase-3 graph for one executable-body root.
    ///
    /// The resulting sequence preserves source evaluation order in a form that later frontend CFG ->
    /// LIR lowering can consume directly:
    /// - side-effect-free `pass` and declaration anchors stay as `StatementItem`
    /// - expression work becomes `EvalExprItem`
    /// - `return` value evaluation is emitted before the terminal `StopNode`
    ///
    /// If a reachable unsupported statement still arrives here, that indicates the caller bypassed
    /// the expected preflight contract, so we fail fast instead of silently emitting a partial graph.
    public @NotNull ExecutableBodyBuild buildStraightLineExecutableBody(@NotNull Block rootBlock) {
        Objects.requireNonNull(rootBlock, "rootBlock must not be null");

        var items = new ArrayList<FrontendCfgGraph.SequenceItem>();
        String returnValueId = null;
        for (var statement : rootBlock.statements()) {
            switch (statement) {
                // `pass` has no extra evaluation work, but preserving the statement node keeps the
                // graph anchored to the original source for later lowering and diagnostics.
                case PassStatement passStatement -> items.add(new FrontendCfgGraph.StatementItem(passStatement));
                // Discarded-expression statements still need their value computation represented,
                // because the CFG must preserve execution order even when the produced value is not
                // consumed by a later source statement.
                case ExpressionStatement expressionStatement -> items.add(
                        new FrontendCfgGraph.EvalExprItem(
                                expressionStatement.expression(),
                                nextValueId()
                        )
                );
                case VariableDeclaration variableDeclaration when variableDeclaration.kind() == DeclarationKind.VAR -> {
                    // Keep the initializer value ahead of the declaration item so later body lowering
                    // can preserve source evaluation order while still anchoring the declaration AST.
                    // Variable initializers use declaration-derived value ids so later debug output
                    // and graph inspection can tie the temporary back to the source slot more
                    // directly than the generic `vN` naming used for other linear evaluations.
                    var initializer = variableDeclaration.value();
                    if (initializer != null) {
                        items.add(new FrontendCfgGraph.EvalExprItem(
                                initializer,
                                nextVariableValueId(variableDeclaration.name())
                        ));
                    }
                    items.add(new FrontendCfgGraph.StatementItem(variableDeclaration));
                }
                case ReturnStatement returnStatement -> {
                    // The return value, when present, is still ordinary linear evaluation that must
                    // happen before control leaves the function. The `StopNode` then records which
                    // frontend-local value id represents the final result of the lexical path.
                    var returnValue = returnStatement.value();
                    if (returnValue != null) {
                        returnValueId = nextValueId();
                        items.add(new FrontendCfgGraph.EvalExprItem(returnValue, returnValueId));
                    }
                    var graph = buildGraph(items, returnValueId);
                    return new ExecutableBodyBuild(graph, new FrontendCfgRegion.BlockRegion(graph.entryNodeId()));
                }
                default -> throw unsupportedReachableStatement(statement);
            }
        }

        var graph = buildGraph(items, null);
        return new ExecutableBodyBuild(graph, new FrontendCfgRegion.BlockRegion(graph.entryNodeId()));
    }

    /// Materializes the minimal phase-3 topology.
    ///
    /// Keeping this in one helper ensures the entry sequence, terminal stop, and deterministic node
    /// ids stay uniform across empty functions, fallthrough functions, and early-return functions.
    private @NotNull FrontendCfgGraph buildGraph(
            @NotNull List<FrontendCfgGraph.SequenceItem> items,
            String returnValueIdOrNull
    ) {
        var entryId = nextSequenceId();
        var stopId = nextStopId();
        var nodes = new LinkedHashMap<String, FrontendCfgGraph.Node>(2);
        nodes.put(entryId, new FrontendCfgGraph.SequenceNode(entryId, items, stopId));
        nodes.put(stopId, new FrontendCfgGraph.StopNode(stopId, returnValueIdOrNull));
        return new FrontendCfgGraph(entryId, nodes);
    }

    /// Sequence ids are lexical-order scoped to one builder instance so tests can assert exact graph
    /// shape without leaking counters across functions.
    private @NotNull String nextSequenceId() {
        return "seq_" + nextSequenceIndex++;
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

    /// Returns whether the given statement can still be represented by the phase-3 single-sequence
    /// model before the first reachable terminator.
    private static boolean isSupportedStraightLineStatement(@NotNull Statement statement) {
        return switch (statement) {
            case PassStatement _, ExpressionStatement _, ReturnStatement _ -> true;
            case VariableDeclaration variableDeclaration -> variableDeclaration.kind() == DeclarationKind.VAR;
            default -> false;
        };
    }

    /// Reaching this path means a caller asked the straight-line builder to materialize a statement
    /// that needs a richer CFG shape than phase 3 provides.
    private static @NotNull IllegalStateException unsupportedReachableStatement(@NotNull Statement statement) {
        return new IllegalStateException(
                "Straight-line frontend CFG builder reached an unsupported reachable statement: "
                        + statement.getClass().getSimpleName()
        );
    }

    /// Build product for one executable-body root.
    ///
    /// The builder returns both artifacts together because later passes typically need:
    /// - the graph itself for node/value traversal
    /// - the root `BlockRegion` for AST-keyed region publication back into `FunctionLoweringContext`
    public record ExecutableBodyBuild(
            @NotNull FrontendCfgGraph graph,
            @NotNull FrontendCfgRegion.BlockRegion rootRegion
    ) {
        public ExecutableBodyBuild {
            Objects.requireNonNull(graph, "graph must not be null");
            Objects.requireNonNull(rootRegion, "rootRegion must not be null");
        }
    }
}
