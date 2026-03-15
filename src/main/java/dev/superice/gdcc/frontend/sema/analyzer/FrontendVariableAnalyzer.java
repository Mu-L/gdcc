package dev.superice.gdcc.frontend.sema.analyzer;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.diagnostic.FrontendRange;
import dev.superice.gdcc.frontend.scope.CallableScope;
import dev.superice.gdcc.frontend.sema.FrontendAnalysisData;
import dev.superice.gdcc.frontend.sema.FrontendAstSideTable;
import dev.superice.gdcc.frontend.sema.FrontendDeclaredTypeSupport;
import dev.superice.gdcc.scope.Scope;
import dev.superice.gdparser.frontend.ast.ASTNodeHandler;
import dev.superice.gdparser.frontend.ast.ASTWalker;
import dev.superice.gdparser.frontend.ast.Block;
import dev.superice.gdparser.frontend.ast.ClassDeclaration;
import dev.superice.gdparser.frontend.ast.ConstructorDeclaration;
import dev.superice.gdparser.frontend.ast.ElifClause;
import dev.superice.gdparser.frontend.ast.ForStatement;
import dev.superice.gdparser.frontend.ast.FrontendASTTraversalDirective;
import dev.superice.gdparser.frontend.ast.FunctionDeclaration;
import dev.superice.gdparser.frontend.ast.IfStatement;
import dev.superice.gdparser.frontend.ast.LambdaExpression;
import dev.superice.gdparser.frontend.ast.MatchStatement;
import dev.superice.gdparser.frontend.ast.Node;
import dev.superice.gdparser.frontend.ast.Parameter;
import dev.superice.gdparser.frontend.ast.SourceFile;
import dev.superice.gdparser.frontend.ast.Statement;
import dev.superice.gdparser.frontend.ast.WhileStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Variable-analysis phase entry point.
///
/// The current phase contract is intentionally narrow and recovery-oriented:
/// - require the skeleton and diagnostics boundaries published by earlier phases
/// - require one top-level `ClassScope` per accepted source file
/// - prefill only function/constructor parameters into `CallableScope`
/// - keep ordinary locals, lambda inventory, `for`, and `match` deferred to later phases
///
/// Parameter binding follows the rewritten plan in `frontend_variable_analyzer_plan.md`:
/// - declaration-directed walking through accepted source/class containers only
/// - skip-and-continue on missing scope records or skipped bad subtrees
/// - `diagnostic + skip` only when a supported parameter targets a non-`CallableScope`
public class FrontendVariableAnalyzer {
    /// Runs variable analysis against the shared analysis carrier.
    ///
    /// Phase 3 enriches the already-published lexical graph with callable parameter inventory while
    /// keeping the public phase seam unchanged for later local-variable work.
    public void analyze(
            @NotNull FrontendAnalysisData analysisData,
            @NotNull DiagnosticManager diagnosticManager
    ) {
        Objects.requireNonNull(analysisData, "analysisData must not be null");
        Objects.requireNonNull(diagnosticManager, "diagnosticManager must not be null");

        var moduleSkeleton = analysisData.moduleSkeleton();
        analysisData.diagnostics();

        // Missing top-level scopes indicate a broken phase boundary rather than a recoverable
        // source error, so this remains a fail-fast guard rail.
        var scopesByAst = analysisData.scopesByAst();
        for (var sourceClassRelation : moduleSkeleton.sourceClassRelations()) {
            var sourceFile = sourceClassRelation.unit().ast();
            if (!scopesByAst.containsKey(sourceFile)) {
                throw new IllegalStateException(
                        "Scope graph has not been published for source file: " + sourceClassRelation.unit().path()
                );
            }
        }

        for (var sourceClassRelation : moduleSkeleton.sourceClassRelations()) {
            new AstWalkerParameterBinder(
                    sourceClassRelation.unit().path(),
                    scopesByAst,
                    diagnosticManager
            ).walk(sourceClassRelation.unit().ast());
        }
    }

    /// ASTWalker-backed declaration-directed handler used by the MVP variable phase.
    ///
    /// `ASTWalker` is used here only as the typed dispatch mechanism. The variable phase still
    /// keeps explicit subtree control so deferred domains remain sealed:
    /// - only source/class statement lists and supported executable blocks are descended into
    /// - function/constructor parameters are bound at the callable boundary
    /// - lambda / `for` / `match` subtrees are pruned explicitly
    /// - arbitrary expression children stay unvisited in this phase
    private static final class AstWalkerParameterBinder implements ASTNodeHandler {
        private final @NotNull Path sourcePath;
        private final @NotNull FrontendAstSideTable<Scope> scopesByAst;
        private final @NotNull DiagnosticManager diagnosticManager;
        private final @NotNull ASTWalker astWalker;

        private AstWalkerParameterBinder(
                @NotNull Path sourcePath,
                @NotNull FrontendAstSideTable<Scope> scopesByAst,
                @NotNull DiagnosticManager diagnosticManager
        ) {
            this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
            this.scopesByAst = Objects.requireNonNull(scopesByAst, "scopesByAst");
            this.diagnosticManager = Objects.requireNonNull(diagnosticManager, "diagnosticManager");
            this.astWalker = new ASTWalker(this);
        }

        private void walk(@NotNull SourceFile sourceFile) {
            astWalker.walk(sourceFile);
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleNode(@NotNull Node node) {
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleSourceFile(@NotNull SourceFile sourceFile) {
            walkStatements(sourceFile.statements());
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleClassDeclaration(@NotNull ClassDeclaration classDeclaration) {
            if (isNotPublished(classDeclaration)) {
                return FrontendASTTraversalDirective.SKIP_CHILDREN;
            }
            astWalker.walk(classDeclaration.body());
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleFunctionDeclaration(
                @NotNull FunctionDeclaration functionDeclaration
        ) {
            bindCallableParameters(functionDeclaration, functionDeclaration.parameters(), functionDeclaration.body());
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleConstructorDeclaration(
                @NotNull ConstructorDeclaration constructorDeclaration
        ) {
            bindCallableParameters(constructorDeclaration, constructorDeclaration.parameters(), constructorDeclaration.body());
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleBlock(@NotNull Block block) {
            if (isNotPublished(block)) {
                return FrontendASTTraversalDirective.SKIP_CHILDREN;
            }
            walkStatements(block.statements());
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleIfStatement(@NotNull IfStatement ifStatement) {
            if (isNotPublished(ifStatement)) {
                return FrontendASTTraversalDirective.SKIP_CHILDREN;
            }
            astWalker.walk(ifStatement.body());
            for (var elifClause : ifStatement.elifClauses()) {
                astWalker.walk(elifClause);
            }
            if (ifStatement.elseBody() != null) {
                astWalker.walk(ifStatement.elseBody());
            }
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleElifClause(@NotNull ElifClause elifClause) {
            if (isNotPublished(elifClause)) {
                return FrontendASTTraversalDirective.SKIP_CHILDREN;
            }
            astWalker.walk(elifClause.body());
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleWhileStatement(@NotNull WhileStatement whileStatement) {
            if (isNotPublished(whileStatement)) {
                return FrontendASTTraversalDirective.SKIP_CHILDREN;
            }
            astWalker.walk(whileStatement.body());
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleLambdaExpression(@NotNull LambdaExpression lambdaExpression) {
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleForStatement(@NotNull ForStatement forStatement) {
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        @Override
        public @NotNull FrontendASTTraversalDirective handleMatchStatement(@NotNull MatchStatement matchStatement) {
            return FrontendASTTraversalDirective.SKIP_CHILDREN;
        }

        private void bindCallableParameters(
                @NotNull Node callableOwner,
                @NotNull List<Parameter> parameters,
                @NotNull Block body
        ) {
            if (isNotPublished(callableOwner)) {
                return;
            }
            for (var parameter : parameters) {
                bindParameter(parameter);
            }
            astWalker.walk(body);
        }

        private void walkStatements(@NotNull List<Statement> statements) {
            for (var statement : statements) {
                astWalker.walk(statement);
            }
        }

        private void bindParameter(@NotNull Parameter parameter) {
            var parameterName = parameter.name().trim();
            warnIgnoredDefaultValue(parameter);

            var targetScope = scopesByAst.get(parameter);
            if (targetScope == null) {
                return;
            }
            if (!(targetScope instanceof CallableScope callableScope)) {
                reportBindingError(
                        parameter,
                        "Parameter '" + parameterName + "' expected CallableScope, but found "
                                + targetScope.getClass().getSimpleName()
                );
                return;
            }

            var parameterType = FrontendDeclaredTypeSupport.resolveTypeOrVariant(
                    parameter.type(),
                    callableScope,
                    sourcePath,
                    diagnosticManager
            );
            var existingBinding = callableScope.resolveValueHere(parameterName);
            if (existingBinding != null) {
                reportBindingError(parameter, switch (existingBinding.kind()) {
                    case PARAMETER -> "Duplicate parameter '" + parameterName + "' in the same callable";
                    case CAPTURE ->
                            "Parameter '" + parameterName + "' conflicts with existing capture '" + parameterName + "'";
                    default ->
                            "Parameter '" + parameterName + "' conflicts with existing callable binding '" + parameterName + "'";
                });
                return;
            }
            callableScope.defineParameter(parameterName, parameterType, parameter);
        }

        private void warnIgnoredDefaultValue(@NotNull Parameter parameter) {
            if (parameter.defaultValue() == null) {
                return;
            }
            diagnosticManager.warning(
                    "sema.unsupported_parameter_default_value",
                    "Parameter default value for '" + parameter.name().trim()
                            + "' is deferred until FrontendExprTypeAnalyzer is implemented; "
                            + "current variable phase ignores the default value expression",
                    sourcePath,
                    FrontendRange.fromAstRange(parameter.defaultValue().range())
            );
        }

        private void reportBindingError(
                @NotNull Parameter parameter,
                @NotNull String message
        ) {
            diagnosticManager.error(
                    "sema.variable_binding",
                    message,
                    sourcePath,
                    FrontendRange.fromAstRange(parameter.range())
            );
        }

        private boolean isNotPublished(@Nullable Node astNode) {
            return astNode == null || !scopesByAst.containsKey(astNode);
        }
    }
}
