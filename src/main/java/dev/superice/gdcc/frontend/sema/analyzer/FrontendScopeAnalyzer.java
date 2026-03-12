package dev.superice.gdcc.frontend.sema.analyzer;

import dev.superice.gdparser.frontend.ast.*;
import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.sema.FrontendAnalysisData;
import dev.superice.gdcc.frontend.sema.FrontendAstSideTable;
import dev.superice.gdcc.frontend.sema.FrontendModuleSkeleton;
import dev.superice.gdcc.frontend.scope.BlockScope;
import dev.superice.gdcc.frontend.scope.BlockScopeKind;
import dev.superice.gdcc.frontend.scope.CallableScope;
import dev.superice.gdcc.frontend.scope.CallableScopeKind;
import dev.superice.gdcc.frontend.scope.ClassScope;
import dev.superice.gdcc.scope.ClassDef;
import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdcc.scope.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/// Scope-phase worker that sits between skeleton publication and future binder/body passes.
///
/// Phase 3 extends the lexical scope graph described in
/// `scope_analyzer_implementation_plan.md`:
/// - top-level script `ClassScope` per `SourceFile`
/// - callable `CallableScope` for functions, constructors, and lambdas
/// - dedicated callable-body `BlockScope`
/// - dedicated control-flow `BlockScope`s for `if` / `elif` / `else`, `while`, `for`, and
///   `match` branches
/// - scope side-table entries for parameters, return types, default-value expressions,
///   control-flow conditions, loop iterables, and match patterns/guards
///
/// It still intentionally defers:
/// - inner-class lexical boundaries
/// - parameter prefill, captures, and other bindings
///
/// Keeping this class separate from `frontend.scope` preserves the layering boundary between
/// protocol objects and semantic-phase orchestration.
public class FrontendScopeAnalyzer {
    /// Runs the scope phase against the shared analysis carrier.
    ///
    /// The scope phase requires the previous skeleton phase to have already published both:
    /// - `moduleSkeleton()`
    /// - the diagnostics snapshot captured right after skeleton
    ///
    /// Once that boundary is present, the analyzer rebuilds `scopesByAst` from scratch so later
    /// phases see one stable lexical-scope side table instead of incrementally mutated leftovers.
    public void analyze(
            @NotNull ClassRegistry classRegistry,
            @NotNull FrontendAnalysisData analysisData,
            @NotNull DiagnosticManager diagnosticManager
    ) {
        Objects.requireNonNull(classRegistry, "classRegistry must not be null");
        Objects.requireNonNull(analysisData, "analysisData must not be null");
        Objects.requireNonNull(diagnosticManager, "diagnosticManager must not be null");

        // Phase ordering matters: scope analysis is defined to start only after skeleton facts and
        // the corresponding boundary snapshot have both become observable to later phases.
        var moduleSkeleton = analysisData.moduleSkeleton();
        analysisData.diagnostics();

        var scopesByAst = new FrontendAstSideTable<Scope>();
        new ScopeBuildingHandler(classRegistry, moduleSkeleton, scopesByAst).walk();
        analysisData.updateScopesByAst(scopesByAst);
    }

    /// Phase-3 scope builder that reacts to nodes traversed by `gdparser`'s built-in `ASTWalker`.
    ///
    /// The handler keeps semantic policy local while delegating traversal mechanics to the parser
    /// library:
    /// - `SourceFile` creates one top-level `ClassScope`
    /// - callables create their own `CallableScope`
    /// - callable bodies create a separate `BlockScope`
    /// - control-flow bodies create dedicated branch/loop `BlockScope`s
    ///
    /// Everything else is either:
    /// - visited under the current already-established lexical scope, or
    /// - skipped on purpose because its dedicated scope semantics belong to a later phase.
    private static final class ScopeBuildingHandler implements ASTNodeHandler {
        private final @NotNull ClassRegistry classRegistry;
        private final @NotNull FrontendAstSideTable<Scope> scopesByAst;
        private final @NotNull List<SourceFile> sourceFilesInOrder;
        private final @NotNull IdentityHashMap<SourceFile, ClassDef> topLevelClassBySourceFile = new IdentityHashMap<>();
        private final @NotNull ArrayDeque<Scope> scopeStack = new ArrayDeque<>();
        private final @NotNull ASTWalker astWalker;

        private ScopeBuildingHandler(
                @NotNull ClassRegistry classRegistry,
                @NotNull FrontendModuleSkeleton moduleSkeleton,
                @NotNull FrontendAstSideTable<Scope> scopesByAst
        ) {
            this.classRegistry = Objects.requireNonNull(classRegistry, "classRegistry");
            this.scopesByAst = Objects.requireNonNull(scopesByAst, "scopesByAst");
            sourceFilesInOrder = indexTopLevelClassesBySourceFile(
                    Objects.requireNonNull(moduleSkeleton, "moduleSkeleton")
            );
            astWalker = new ASTWalker(this);
        }

        private void walk() {
            for (var sourceFile : sourceFilesInOrder) {
                astWalker.walk(sourceFile);
            }
            if (!scopeStack.isEmpty()) {
                throw new IllegalStateException("Scope stack must be empty after AST traversal");
            }
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleNode(@NotNull Node node) {
            recordScope(node, currentScope());
            return FrontendASTTraversalDirective.CONTINUE;
        }

        /// Each parsed source unit now carries an explicit skeleton relation with exactly one
        /// top-level script class and zero or more nested classes. Phase 3 still materializes the
        /// source-wide `ClassScope` only from the top-level script class; nested classes remain a
        /// later-phase concern.
        @Override
        public @NotNull FrontendASTTraversalDirective handleSourceFile(@NotNull SourceFile sourceFile) {
            var sourceFileScope = new ClassScope(
                    classRegistry,
                    classRegistry,
                    requireTopLevelClassDef(sourceFile)
            );
            recordScope(sourceFile, sourceFileScope);
            withCurrentScope(sourceFileScope, () -> walkChildren(sourceFile));
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleFunctionDeclaration(
                @NotNull FunctionDeclaration functionDeclaration
        ) {
            visitCallableBoundary(
                    functionDeclaration,
                    functionDeclaration.parameters(),
                    functionDeclaration.returnType(),
                    functionDeclaration.body(),
                    new CallableScope(currentScope(), CallableScopeKind.FUNCTION_DECLARATION),
                    BlockScopeKind.FUNCTION_BODY
            );
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleConstructorDeclaration(
                @NotNull ConstructorDeclaration constructorDeclaration
        ) {
            visitCallableBoundary(
                    constructorDeclaration,
                    constructorDeclaration.parameters(),
                    constructorDeclaration.returnType(),
                    constructorDeclaration.body(),
                    new CallableScope(currentScope(), CallableScopeKind.CONSTRUCTOR_DECLARATION),
                    BlockScopeKind.CONSTRUCTOR_BODY
            );
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleLambdaExpression(
                @NotNull LambdaExpression lambdaExpression
        ) {
            visitCallableBoundary(
                    lambdaExpression,
                    lambdaExpression.parameters(),
                    lambdaExpression.returnType(),
                    lambdaExpression.body(),
                    new CallableScope(currentScope(), CallableScopeKind.LAMBDA_EXPRESSION),
                    BlockScopeKind.LAMBDA_BODY
            );
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleBlock(@NotNull Block node) {
            visitBlockBoundary(node, BlockScopeKind.BLOCK_STATEMENT);
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleIfStatement(@NotNull IfStatement ifStatement) {
            recordScope(ifStatement, currentScope());
            astWalker.walk(ifStatement.condition());
            visitBlockBoundary(ifStatement.body(), BlockScopeKind.IF_BODY);
            walkNodes(ifStatement.elifClauses());
            if (ifStatement.elseBody() != null) {
                visitBlockBoundary(ifStatement.elseBody(), BlockScopeKind.ELSE_BODY);
            }
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleElifClause(@NotNull ElifClause elifClause) {
            recordScope(elifClause, currentScope());
            astWalker.walk(elifClause.condition());
            visitBlockBoundary(elifClause.body(), BlockScopeKind.ELIF_BODY);
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleWhileStatement(@NotNull WhileStatement whileStatement) {
            recordScope(whileStatement, currentScope());
            astWalker.walk(whileStatement.condition());
            visitBlockBoundary(whileStatement.body(), BlockScopeKind.WHILE_BODY);
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleForStatement(@NotNull ForStatement forStatement) {
            recordScope(forStatement, currentScope());
            if (forStatement.iteratorType() != null) {
                astWalker.walk(forStatement.iteratorType());
            }
            astWalker.walk(forStatement.iterable());
            visitBlockBoundary(forStatement.body(), BlockScopeKind.FOR_BODY);
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleMatchStatement(@NotNull MatchStatement matchStatement) {
            recordScope(matchStatement, currentScope());
            astWalker.walk(matchStatement.value());
            walkNodes(matchStatement.sections());
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleClassDeclaration(@NotNull ClassDeclaration node) {
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleMatchSection(@NotNull MatchSection node) {
            var branchScope = new BlockScope(currentScope(), BlockScopeKind.MATCH_SECTION_BODY);
            recordScope(node, branchScope);
            withCurrentScope(branchScope, () -> {
                walkNodes(node.patterns());
                if (node.guard() != null) {
                    astWalker.walk(node.guard());
                }
                recordScope(node.body(), branchScope);
                walkNodes(node.body().statements());
            });
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        /// All callable-like regions share the same two-layer shape:
        /// - callable declaration/expression -> `CallableScope`
        /// - executable body block -> dedicated `BlockScope`
        ///
        /// Parameters and return types are traversed under the callable layer, while executable
        /// statements descend under the separate body block layer. Variable bindings remain
        /// deferred; the handler only records lexical-scope facts.
        private void visitCallableBoundary(
                @NotNull Node callableOwner,
                @NotNull List<Parameter> parameters,
                @Nullable TypeRef returnType,
                @NotNull Block body,
                @NotNull CallableScope callableScope,
                @NotNull BlockScopeKind bodyKind
        ) {
            recordScope(callableOwner, callableScope);
            withCurrentScope(callableScope, () -> {
                walkNodes(parameters);
                if (returnType != null) {
                    astWalker.walk(returnType);
                }
            });

            var bodyScope = new BlockScope(callableScope, bodyKind);
            recordScope(body, bodyScope);
            withCurrentScope(bodyScope, () -> walkNodes(body.statements()));
        }

        /// Creates one lexical `BlockScope` for a block boundary that should behave as a fresh
        /// local region, then walks only the statements inside that block under the new scope.
        ///
        /// The caller is responsible for deciding when the surrounding expression context should
        /// stay in the outer scope. For example:
        /// - `if` / `elif` / `while` conditions remain in the enclosing scope
        /// - `for` iterator type and iterable remain in the enclosing scope
        /// - only the executable block body switches to the new `BlockScope`
        private void visitBlockBoundary(@NotNull Block block, @NotNull BlockScopeKind kind) {
            var blockScope = new BlockScope(currentScope(), kind);
            recordScope(block, blockScope);
            withCurrentScope(blockScope, () -> walkNodes(block.statements()));
        }

        private void withCurrentScope(@NotNull Scope scope, @NotNull Runnable action) {
            scopeStack.push(Objects.requireNonNull(scope, "scope"));
            try {
                action.run();
            } finally {
                scopeStack.pop();
            }
        }

        private void walkChildren(@NotNull Node node) {
            walkNodes(node.getChildren());
        }

        private void walkNodes(@NotNull Iterable<? extends Node> nodes) {
            for (var node : nodes) {
                astWalker.walk(node);
            }
        }

        private void recordScope(@NotNull Node astNode, @NotNull Scope scope) {
            scopesByAst.put(astNode, scope);
        }

        private @NotNull Scope currentScope() {
            var scope = scopeStack.peek();
            if (scope == null) {
                throw new IllegalStateException("Current lexical scope is unavailable during AST traversal");
            }
            return scope;
        }

        private @NotNull ClassDef requireTopLevelClassDef(@NotNull SourceFile sourceFile) {
            var classDef = topLevelClassBySourceFile.get(sourceFile);
            if (classDef == null) {
                throw new IllegalStateException(
                        "No top-level class skeleton was indexed for SourceFile@" + System.identityHashCode(sourceFile)
                );
            }
            return classDef;
        }

        private @NotNull List<SourceFile> indexTopLevelClassesBySourceFile(
                @NotNull FrontendModuleSkeleton moduleSkeleton
        ) {
            var sourceClassRelations = moduleSkeleton.sourceClassRelations();
            var sourceFiles = new ArrayList<SourceFile>(sourceClassRelations.size());
            for (var sourceClassRelation : sourceClassRelations) {
                var sourceFile = sourceClassRelation.unit().ast();
                sourceFiles.add(sourceFile);
                var previous = topLevelClassBySourceFile.put(sourceFile, sourceClassRelation.topLevelClassDef());
                if (previous != null) {
                    throw new IllegalStateException(
                        "Duplicate SourceFile encountered while indexing top-level classes"
                    );
                }
            }
            return List.copyOf(sourceFiles);
        }
    }
}
