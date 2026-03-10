package dev.superice.gdcc.frontend.sema;

import dev.superice.gdcc.frontend.diagnostic.FrontendDiagnostic;
import dev.superice.gdcc.scope.Scope;
import dev.superice.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/// Unified semantic-analysis result container backed by multiple AST side tables.
///
/// For now this result only has one populated table: annotations attached to AST objects.
/// The remaining tables are created up front so later interface/body work can extend the
/// analyzer without changing the public result topology again.
public final class FrontendAnalysisResult {
    private final @NotNull FrontendModuleSkeleton moduleSkeleton;
    private final @NotNull List<FrontendDiagnostic> diagnostics;
    private final @NotNull FrontendAstSideTable<List<FrontendGdAnnotation>> annotationsByAst;
    private final @NotNull FrontendAstSideTable<Scope> scopesByAst;
    private final @NotNull FrontendAstSideTable<FrontendBinding> symbolBindings;
    private final @NotNull FrontendAstSideTable<GdType> expressionTypes;
    private final @NotNull FrontendAstSideTable<FrontendResolvedMember> resolvedMembers;
    private final @NotNull FrontendAstSideTable<FrontendResolvedCall> resolvedCalls;

    public FrontendAnalysisResult(
            @NotNull FrontendModuleSkeleton moduleSkeleton,
            @NotNull List<FrontendDiagnostic> diagnostics,
            @NotNull FrontendAstSideTable<List<FrontendGdAnnotation>> annotationsByAst,
            @NotNull FrontendAstSideTable<Scope> scopesByAst,
            @NotNull FrontendAstSideTable<FrontendBinding> symbolBindings,
            @NotNull FrontendAstSideTable<GdType> expressionTypes,
            @NotNull FrontendAstSideTable<FrontendResolvedMember> resolvedMembers,
            @NotNull FrontendAstSideTable<FrontendResolvedCall> resolvedCalls
    ) {
        this.moduleSkeleton = Objects.requireNonNull(moduleSkeleton, "moduleSkeleton must not be null");
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
        this.annotationsByAst = Objects.requireNonNull(annotationsByAst, "annotationsByAst must not be null");
        this.scopesByAst = Objects.requireNonNull(scopesByAst, "scopesByAst must not be null");
        this.symbolBindings = Objects.requireNonNull(symbolBindings, "symbolBindings must not be null");
        this.expressionTypes = Objects.requireNonNull(expressionTypes, "expressionTypes must not be null");
        this.resolvedMembers = Objects.requireNonNull(resolvedMembers, "resolvedMembers must not be null");
        this.resolvedCalls = Objects.requireNonNull(resolvedCalls, "resolvedCalls must not be null");
    }

    public static @NotNull FrontendAnalysisResult bootstrap(
            @NotNull FrontendModuleSkeleton moduleSkeleton,
            @NotNull FrontendAstSideTable<List<FrontendGdAnnotation>> annotationsByAst
    ) {
        Objects.requireNonNull(moduleSkeleton, "moduleSkeleton must not be null");
        return new FrontendAnalysisResult(
                moduleSkeleton,
                moduleSkeleton.diagnostics(),
                Objects.requireNonNull(annotationsByAst, "annotationsByAst must not be null"),
                new FrontendAstSideTable<>(),
                new FrontendAstSideTable<>(),
                new FrontendAstSideTable<>(),
                new FrontendAstSideTable<>(),
                new FrontendAstSideTable<>()
        );
    }

    public @NotNull FrontendModuleSkeleton moduleSkeleton() {
        return moduleSkeleton;
    }

    public @NotNull List<FrontendDiagnostic> diagnostics() {
        return diagnostics;
    }

    public @NotNull FrontendAstSideTable<List<FrontendGdAnnotation>> annotationsByAst() {
        return annotationsByAst;
    }

    public @NotNull FrontendAstSideTable<Scope> scopesByAst() {
        return scopesByAst;
    }

    public @NotNull FrontendAstSideTable<FrontendBinding> symbolBindings() {
        return symbolBindings;
    }

    public @NotNull FrontendAstSideTable<GdType> expressionTypes() {
        return expressionTypes;
    }

    public @NotNull FrontendAstSideTable<FrontendResolvedMember> resolvedMembers() {
        return resolvedMembers;
    }

    public @NotNull FrontendAstSideTable<FrontendResolvedCall> resolvedCalls() {
        return resolvedCalls;
    }
}
