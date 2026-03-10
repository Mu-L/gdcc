package dev.superice.gdcc.frontend.sema;

import dev.superice.gdcc.frontend.parse.FrontendSourceUnit;
import dev.superice.gdcc.scope.ClassRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/// Basic frontend semantic-analyzer framework.
///
/// This round intentionally stops at framework wiring: it builds the existing skeleton result,
/// initializes all planned side tables, and populates the annotation side table from the full AST.
/// Binder, expression typing, member/call resolution, and diagnostics beyond the skeleton stage
/// remain future work.
public final class FrontendSemanticAnalyzer {
    private final @NotNull FrontendClassSkeletonBuilder classSkeletonBuilder;
    private final @NotNull FrontendAnnotationCollector annotationCollector;

    public FrontendSemanticAnalyzer() {
        this(new FrontendClassSkeletonBuilder(), new FrontendAnnotationCollector());
    }

    public FrontendSemanticAnalyzer(
            @NotNull FrontendClassSkeletonBuilder classSkeletonBuilder,
            @NotNull FrontendAnnotationCollector annotationCollector
    ) {
        this.classSkeletonBuilder = Objects.requireNonNull(classSkeletonBuilder, "classSkeletonBuilder must not be null");
        this.annotationCollector = Objects.requireNonNull(annotationCollector, "annotationCollector must not be null");
    }

    public @NotNull FrontendAnalysisResult analyze(
            @NotNull String moduleName,
            @NotNull List<FrontendSourceUnit> units,
            @NotNull ClassRegistry classRegistry
    ) {
        Objects.requireNonNull(moduleName, "moduleName must not be null");
        Objects.requireNonNull(units, "units must not be null");
        Objects.requireNonNull(classRegistry, "classRegistry must not be null");

        var moduleSkeleton = classSkeletonBuilder.build(moduleName, units, classRegistry);
        var annotationsByAst = new FrontendAstSideTable<List<FrontendGdAnnotation>>();
        for (var unit : moduleSkeleton.units()) {
            annotationsByAst.putAll(annotationCollector.collect(unit));
        }
        return FrontendAnalysisResult.bootstrap(moduleSkeleton, annotationsByAst);
    }
}
