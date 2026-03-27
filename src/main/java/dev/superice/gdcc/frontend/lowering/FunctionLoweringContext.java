package dev.superice.gdcc.frontend.lowering;

import dev.superice.gdcc.frontend.sema.FrontendAnalysisData;
import dev.superice.gdcc.frontend.sema.FrontendSourceClassRelation;
import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdcc.lir.LirFunctionDef;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Objects;

/// Lowering-local description of one function-shaped unit that later passes will lower.
///
/// The unit can be:
/// - an executable callable body already published in the class skeleton
/// - a synthetic property initializer function shell
/// - a future synthetic parameter-default initializer function shell
///
/// The record deliberately keeps direct AST identity references because frontend side tables are
/// keyed by the original parser nodes.
public record FunctionLoweringContext(
        @NotNull Kind kind,
        @NotNull Path sourcePath,
        @NotNull FrontendSourceClassRelation sourceClassRelation,
        @NotNull LirClassDef owningClass,
        @NotNull LirFunctionDef targetFunction,
        @NotNull Node sourceOwner,
        @NotNull Node loweringRoot,
        @NotNull FrontendAnalysisData analysisData
) {
    public FunctionLoweringContext {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        Objects.requireNonNull(sourceClassRelation, "sourceClassRelation must not be null");
        Objects.requireNonNull(owningClass, "owningClass must not be null");
        Objects.requireNonNull(targetFunction, "targetFunction must not be null");
        Objects.requireNonNull(sourceOwner, "sourceOwner must not be null");
        Objects.requireNonNull(loweringRoot, "loweringRoot must not be null");
        Objects.requireNonNull(analysisData, "analysisData must not be null");
    }

    public enum Kind {
        EXECUTABLE_BODY,
        PROPERTY_INIT,
        PARAMETER_DEFAULT_INIT
    }
}
