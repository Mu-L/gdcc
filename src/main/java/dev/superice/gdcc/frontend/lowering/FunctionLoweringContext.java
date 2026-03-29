package dev.superice.gdcc.frontend.lowering;

import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import dev.superice.gdcc.frontend.lowering.cfg.FrontendCfgRegion;
import dev.superice.gdcc.frontend.sema.FrontendAnalysisData;
import dev.superice.gdcc.frontend.sema.FrontendAstSideTable;
import dev.superice.gdcc.frontend.sema.FrontendSourceClassRelation;
import dev.superice.gdcc.lir.LirBasicBlock;
import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdcc.lir.LirFunctionDef;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/// Lowering-local description of one function-shaped unit that later passes will lower.
///
/// The unit can be:
/// - an executable callable body already published in the class skeleton
/// - a synthetic property initializer function shell
/// - a future synthetic parameter-default initializer function shell
///
/// The carrier keeps direct AST identity references because frontend side tables are keyed by the
/// original parser nodes. Later CFG/body passes therefore use the same AST identities instead of
/// rebuilding separate synthetic lookup keys.
public final class FunctionLoweringContext {
    /// Category of the lowering unit.
    ///
    /// This determines how later passes should interpret the relationship between `sourceOwner`
    /// and `loweringRoot`. The preparation pass currently publishes `EXECUTABLE_BODY` and
    /// `PROPERTY_INIT`, while `PARAMETER_DEFAULT_INIT` remains a reserved extension slot.
    private final @NotNull Kind kind;

    /// Source file path that owns this lowering unit.
    ///
    /// It must point at the same parsed source unit as `sourceClassRelation` and `sourceOwner` so
    /// diagnostics, runtime-name mapping, and future lowering failures can still anchor back to
    /// the original file.
    private final @NotNull Path sourcePath;

    /// Source-to-class relation published by the frontend skeleton.
    ///
    /// This relation is the stable bridge from source AST ownership to the runtime-mapped
    /// `LirClassDef`, including top-level and inner-class ownership facts already frozen by the
    /// skeleton phase.
    private final @NotNull FrontendSourceClassRelation sourceClassRelation;

    /// LIR class that owns the target function shell.
    ///
    /// For executable bodies this is the class that already contains the callable skeleton. For
    /// property/default initializer units this is the class that receives the hidden synthetic
    /// helper function.
    private final @NotNull LirClassDef owningClass;

    /// Target function whose body will be populated by lowering.
    ///
    /// During preparation the only requirement is that this function shell already exists on the
    /// owning class. Later CFG/body passes are responsible for filling blocks, entry metadata, and
    /// instructions into this shell when the architecture allows materialization.
    private final @NotNull LirFunctionDef targetFunction;

    /// Original declaration-level AST owner used by shared frontend side tables.
    ///
    /// This intentionally stays at the declaration node instead of collapsing to `loweringRoot`:
    /// callable lowering uses the declaration, property initialization uses the property
    /// declaration, and future parameter-default lowering uses the parameter/default declaration.
    private final @NotNull Node sourceOwner;

    /// AST root actually traversed and transformed by this lowering unit.
    ///
    /// It can be identical to `sourceOwner` or a narrower subtree below it. For example, property
    /// initialization lowers only the initializer expression, while executable lowering uses the
    /// callable body `Block`.
    private final @NotNull Node loweringRoot;

    /// Compile-ready frontend analysis snapshot reused by all later lowering passes.
    ///
    /// CFG/body lowering must read scopes, bindings, resolved members/calls, and expression types
    /// from this snapshot instead of re-running semantic analysis or rebuilding ad-hoc side
    /// tables.
    private final @NotNull FrontendAnalysisData analysisData;

    /// Frontend-only CFG graph published by the future `FrontendLoweringBuildCfgPass`.
    ///
    /// The graph carrier is separate from LIR basic blocks so frontend can first freeze source
    /// control-flow, condition-evaluation regions, and short-circuit structure before any
    /// frontend CFG -> LIR lowering starts writing real blocks.
    private @Nullable FrontendCfgGraph frontendCfgGraph;

    /// AST-keyed structured regions inside `frontendCfgGraph`.
    ///
    /// These regions are the new source-of-truth entry/merge/exit anchors for structured source
    /// nodes such as `Block`, `if`/`elif`, and `while`. Like the semantic side tables, the lookup
    /// deliberately uses AST identity instead of structural equality.
    private final @NotNull FrontendAstSideTable<FrontendCfgRegion> frontendCfgRegions = new FrontendAstSideTable<>();

    /// Legacy per-function CFG skeleton bundles keyed by original AST node identity.
    ///
    /// This side table currently exists only for the metadata-only `FrontendLoweringCfgPass`.
    /// It freezes deterministic AST-to-role bookkeeping for the migration period, but the new
    /// frontend CFG architecture is represented by `frontendCfgGraph` plus `frontendCfgRegions`.
    @Deprecated(since = "2026-03-29")
    private final @NotNull FrontendAstSideTable<CfgNodeBlocks> cfgNodeBlocks = new FrontendAstSideTable<>();

    public FunctionLoweringContext(
            @NotNull Kind kind,
            @NotNull Path sourcePath,
            @NotNull FrontendSourceClassRelation sourceClassRelation,
            @NotNull LirClassDef owningClass,
            @NotNull LirFunctionDef targetFunction,
            @NotNull Node sourceOwner,
            @NotNull Node loweringRoot,
            @NotNull FrontendAnalysisData analysisData
    ) {
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        this.sourceClassRelation = Objects.requireNonNull(
                sourceClassRelation,
                "sourceClassRelation must not be null"
        );
        this.owningClass = Objects.requireNonNull(owningClass, "owningClass must not be null");
        this.targetFunction = Objects.requireNonNull(targetFunction, "targetFunction must not be null");
        this.sourceOwner = Objects.requireNonNull(sourceOwner, "sourceOwner must not be null");
        this.loweringRoot = Objects.requireNonNull(loweringRoot, "loweringRoot must not be null");
        this.analysisData = Objects.requireNonNull(analysisData, "analysisData must not be null");
    }

    public @NotNull Kind kind() {
        return kind;
    }

    public @NotNull Path sourcePath() {
        return sourcePath;
    }

    public @NotNull FrontendSourceClassRelation sourceClassRelation() {
        return sourceClassRelation;
    }

    public @NotNull LirClassDef owningClass() {
        return owningClass;
    }

    public @NotNull LirFunctionDef targetFunction() {
        return targetFunction;
    }

    public @NotNull Node sourceOwner() {
        return sourceOwner;
    }

    public @NotNull Node loweringRoot() {
        return loweringRoot;
    }

    public @NotNull FrontendAnalysisData analysisData() {
        return analysisData;
    }

    /// Publishes the immutable frontend CFG graph for this lowering unit.
    ///
    /// Duplicate publication is rejected so later passes can rely on one stable frontend CFG graph
    /// snapshot per function context.
    public void publishFrontendCfgGraph(@NotNull FrontendCfgGraph frontendCfgGraph) {
        Objects.requireNonNull(frontendCfgGraph, "frontendCfgGraph must not be null");
        if (this.frontendCfgGraph != null) {
            throw new IllegalStateException("Frontend CFG graph has already been published");
        }
        this.frontendCfgGraph = frontendCfgGraph;
    }

    public @Nullable FrontendCfgGraph frontendCfgGraphOrNull() {
        return frontendCfgGraph;
    }

    public @NotNull FrontendCfgGraph requireFrontendCfgGraph() {
        if (frontendCfgGraph == null) {
            throw new IllegalStateException("Frontend CFG graph has not been published yet");
        }
        return frontendCfgGraph;
    }

    public boolean hasFrontendCfgGraph() {
        return frontendCfgGraph != null;
    }

    /// Publishes one AST-keyed frontend CFG region for this lowering unit.
    ///
    /// The mapping must remain one-to-one by AST identity so later passes can recover the region
    /// entry/merge/exit anchors for the original source node without rebuilding the region lookup.
    public void publishFrontendCfgRegion(@NotNull Node astNode, @NotNull FrontendCfgRegion region) {
        Objects.requireNonNull(astNode, "astNode must not be null");
        Objects.requireNonNull(region, "region must not be null");
        if (frontendCfgRegions.containsKey(astNode)) {
            throw new IllegalStateException(
                    "Frontend CFG region has already been published for " + describeCfgAstNode(astNode)
            );
        }
        frontendCfgRegions.put(astNode, region);
    }

    public @Nullable FrontendCfgRegion frontendCfgRegionOrNull(@NotNull Node astNode) {
        return frontendCfgRegions.get(Objects.requireNonNull(astNode, "astNode must not be null"));
    }

    public @NotNull FrontendCfgRegion requireFrontendCfgRegion(@NotNull Node astNode) {
        var region = frontendCfgRegionOrNull(astNode);
        if (region == null) {
            throw new IllegalStateException(
                    "Frontend CFG region has not been published for " + describeCfgAstNode(astNode)
            );
        }
        return region;
    }

    public boolean hasFrontendCfgRegion(@NotNull Node astNode) {
        return frontendCfgRegions.containsKey(Objects.requireNonNull(astNode, "astNode must not be null"));
    }

    /// Publishes one AST-keyed CFG block bundle for this lowering unit.
    ///
    /// Duplicate publication for the same AST node is a protocol violation because later passes
    /// must be able to rely on a one-to-one mapping from node identity to block-role bundle.
    ///
    /// @deprecated Legacy migration-only API for `FrontendLoweringCfgPass`. New frontend CFG work
    /// should publish `frontendCfgGraph` plus `frontendCfgRegions` instead.
    @Deprecated(since = "2026-03-29")
    public void publishCfgNodeBlocks(@NotNull Node astNode, @NotNull CfgNodeBlocks blocks) {
        Objects.requireNonNull(astNode, "astNode must not be null");
        Objects.requireNonNull(blocks, "blocks must not be null");
        if (cfgNodeBlocks.containsKey(astNode)) {
            throw new IllegalStateException(
                    "CFG node blocks have already been published for " + describeCfgAstNode(astNode)
            );
        }
        cfgNodeBlocks.put(astNode, blocks);
    }

    /// @deprecated Legacy migration-only API for `FrontendLoweringCfgPass`. New frontend CFG work
    /// should read `frontendCfgGraph` plus `frontendCfgRegions` instead.
    @Deprecated(since = "2026-03-29")
    public @Nullable CfgNodeBlocks cfgNodeBlocksOrNull(@NotNull Node astNode) {
        return cfgNodeBlocks.get(Objects.requireNonNull(astNode, "astNode must not be null"));
    }

    /// @deprecated Legacy migration-only API for `FrontendLoweringCfgPass`. New frontend CFG work
    /// should read `frontendCfgGraph` plus `frontendCfgRegions` instead.
    @Deprecated(since = "2026-03-29")
    public @NotNull CfgNodeBlocks requireCfgNodeBlocks(@NotNull Node astNode) {
        var blocks = cfgNodeBlocksOrNull(astNode);
        if (blocks == null) {
            throw new IllegalStateException(
                    "CFG node blocks have not been published for " + describeCfgAstNode(astNode)
            );
        }
        return blocks;
    }

    /// @deprecated Legacy migration-only API for `FrontendLoweringCfgPass`. New frontend CFG work
    /// should read `frontendCfgGraph` plus `frontendCfgRegions` instead.
    @Deprecated(since = "2026-03-29")
    public boolean hasCfgNodeBlocks(@NotNull Node astNode) {
        return cfgNodeBlocks.containsKey(Objects.requireNonNull(astNode, "astNode must not be null"));
    }

    public enum Kind {
        EXECUTABLE_BODY,
        PROPERTY_INIT,
        PARAMETER_DEFAULT_INIT
    }

    /// Legacy role-bearing CFG skeleton blocks published for one AST node.
    ///
    /// These bundles are a migration aid for the current metadata-only CFG pass. They intentionally
    /// stop short of expressing full source control-flow semantics and should eventually be
    /// replaced by frontend CFG graph regions.
    @Deprecated(since = "2026-03-29")
    public sealed interface CfgNodeBlocks
            permits BlockCfgNodeBlocks, IfCfgNodeBlocks, ElifCfgNodeBlocks, WhileCfgNodeBlocks {
        @NotNull List<LirBasicBlock> blocks();
    }

    /// CFG bundle for the executable-body root block.
    @Deprecated(since = "2026-03-29")
    public record BlockCfgNodeBlocks(@NotNull LirBasicBlock entry) implements CfgNodeBlocks {
        public BlockCfgNodeBlocks {
            Objects.requireNonNull(entry, "entry must not be null");
        }

        @Override
        public @NotNull List<LirBasicBlock> blocks() {
            return List.of(entry);
        }
    }

    /// CFG bundle for one `if` statement.
    ///
    /// `elseOrNextClauseEntry` may alias `merge` when the source statement has neither `else` nor
    /// `elif`, because the false path then falls through directly into the merge block.
    @Deprecated(since = "2026-03-29")
    public record IfCfgNodeBlocks(
            @NotNull LirBasicBlock thenEntry,
            @NotNull LirBasicBlock elseOrNextClauseEntry,
            @NotNull LirBasicBlock merge
    ) implements CfgNodeBlocks {
        public IfCfgNodeBlocks {
            Objects.requireNonNull(thenEntry, "thenEntry must not be null");
            Objects.requireNonNull(elseOrNextClauseEntry, "elseOrNextClauseEntry must not be null");
            Objects.requireNonNull(merge, "merge must not be null");
        }

        @Override
        public @NotNull List<LirBasicBlock> blocks() {
            return distinctBlocks(thenEntry, elseOrNextClauseEntry, merge);
        }
    }

    /// CFG bundle for one `elif` clause.
    ///
    /// `nextClauseOrMerge` may alias the owning `if` merge block when this clause is the final
    /// false-continuation point in the chain.
    @Deprecated(since = "2026-03-29")
    public record ElifCfgNodeBlocks(
            @NotNull LirBasicBlock bodyEntry,
            @NotNull LirBasicBlock nextClauseOrMerge
    ) implements CfgNodeBlocks {
        public ElifCfgNodeBlocks {
            Objects.requireNonNull(bodyEntry, "bodyEntry must not be null");
            Objects.requireNonNull(nextClauseOrMerge, "nextClauseOrMerge must not be null");
        }

        @Override
        public @NotNull List<LirBasicBlock> blocks() {
            return distinctBlocks(bodyEntry, nextClauseOrMerge);
        }
    }

    /// CFG bundle for one `while` statement.
    @Deprecated(since = "2026-03-29")
    public record WhileCfgNodeBlocks(
            @NotNull LirBasicBlock conditionEntry,
            @NotNull LirBasicBlock bodyEntry,
            @NotNull LirBasicBlock exit
    ) implements CfgNodeBlocks {
        public WhileCfgNodeBlocks {
            Objects.requireNonNull(conditionEntry, "conditionEntry must not be null");
            Objects.requireNonNull(bodyEntry, "bodyEntry must not be null");
            Objects.requireNonNull(exit, "exit must not be null");
        }

        @Override
        public @NotNull List<LirBasicBlock> blocks() {
            return distinctBlocks(conditionEntry, bodyEntry, exit);
        }
    }

    private static @NotNull List<LirBasicBlock> distinctBlocks(@NotNull LirBasicBlock... blocks) {
        var seen = new IdentityHashMap<LirBasicBlock, Boolean>();
        var distinct = new ArrayList<LirBasicBlock>(blocks.length);
        for (var block : blocks) {
            if (seen.put(block, true) == null) {
                distinct.add(block);
            }
        }
        return List.copyOf(distinct);
    }

    private static @NotNull String describeCfgAstNode(@NotNull Node astNode) {
        return astNode.getClass().getSimpleName() + "@" + System.identityHashCode(astNode);
    }
}
