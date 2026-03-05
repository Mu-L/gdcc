package dev.superice.gdcc.frontend.parse;

import dev.superice.gdparser.frontend.ast.*;
import dev.superice.gdparser.frontend.lowering.CstToAstMapper;
import dev.superice.gdparser.infra.treesitter.GdParserFacade;
import dev.superice.gdcc.frontend.diagnostic.FrontendDiagnostic;
import dev.superice.gdcc.frontend.diagnostic.FrontendDiagnosticSeverity;
import dev.superice.gdcc.frontend.diagnostic.FrontendRange;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Parse GDScript source into AST and map tolerant lowering diagnostics into GDCC frontend diagnostics.
public final class GdScriptParserService {
    private final @NotNull GdParserFacade parserFacade;
    private final @NotNull CstToAstMapper cstToAstMapper;

    public GdScriptParserService() {
        this(GdParserFacade.withDefaultLanguage(), new CstToAstMapper());
    }

    public GdScriptParserService(@NotNull GdParserFacade parserFacade, @NotNull CstToAstMapper cstToAstMapper) {
        this.parserFacade = Objects.requireNonNull(parserFacade, "parserFacade must not be null");
        this.cstToAstMapper = Objects.requireNonNull(cstToAstMapper, "cstToAstMapper must not be null");
    }

    public @NotNull FrontendSourceUnit parseUnit(@NotNull Path sourcePath, @NotNull String source) {
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        Objects.requireNonNull(source, "source must not be null");
        try {
            var root = parserFacade.parseCstRoot(source);
            var mappingResult = cstToAstMapper.map(source, root);
            var diagnostics = mappingResult.diagnostics().stream()
                    .map(astDiagnostic -> toFrontendDiagnostic(sourcePath, astDiagnostic))
                    .toList();
            return new FrontendSourceUnit(sourcePath, source, mappingResult.ast(), diagnostics);
        } catch (RuntimeException exception) {
            var diagnostics = List.of(FrontendDiagnostic.error(
                    "parse.internal",
                    "Unexpected parser failure: " + exception.getMessage(),
                    sourcePath,
                    null
            ));
            return new FrontendSourceUnit(sourcePath, source, emptySourceFile(), diagnostics);
        }
    }

    private @NotNull FrontendDiagnostic toFrontendDiagnostic(@NotNull Path sourcePath, @NotNull AstDiagnostic diagnostic) {
        var severity = switch (diagnostic.severity()) {
            case ERROR -> FrontendDiagnosticSeverity.ERROR;
            case WARNING -> FrontendDiagnosticSeverity.WARNING;
        };
        return new FrontendDiagnostic(
                severity,
                "parse.lowering",
                diagnostic.message(),
                sourcePath,
                FrontendRange.fromAstRange(diagnostic.range())
        );
    }

    private @NotNull SourceFile emptySourceFile() {
        return new SourceFile(
                List.of(),
                new Range(0, 0, new Point(0, 0), new Point(0, 0))
        );
    }
}
