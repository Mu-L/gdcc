package dev.superice.gdcc.frontend.parse;

import dev.superice.gdparser.frontend.ast.*;
import dev.superice.gdparser.frontend.lowering.CstToAstMapper;
import dev.superice.gdparser.infra.treesitter.GdParserFacade;
import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
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

    /// Parses one source unit and reports this parse call's diagnostics into the shared manager.
    ///
    /// The returned `FrontendSourceUnit` keeps only this parse call's diagnostics as an immutable
    /// phase snapshot. The manager may already contain diagnostics from earlier units in the same
    /// pipeline, so callers must treat the unit snapshot and manager snapshot as different views.
    public @NotNull FrontendSourceUnit parseUnit(
            @NotNull Path sourcePath,
            @NotNull String source,
            @NotNull DiagnosticManager diagnosticManager
    ) {
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(diagnosticManager, "diagnosticManager must not be null");
        try {
            var root = parserFacade.parseCstRoot(source);
            var mappingResult = cstToAstMapper.map(source, root);
            var parseDiagnostics = mappingResult.diagnostics().stream()
                    .map(diagnostic -> new FrontendDiagnostic(
                            switch (diagnostic.severity()) {
                                case ERROR -> FrontendDiagnosticSeverity.ERROR;
                                case WARNING -> FrontendDiagnosticSeverity.WARNING;
                            },
                            "parse.lowering",
                            diagnostic.message(),
                            sourcePath,
                            FrontendRange.fromAstRange(diagnostic.range())
                    ))
                    .toList();
            diagnosticManager.reportAll(parseDiagnostics);
            return new FrontendSourceUnit(sourcePath, source, mappingResult.ast(), parseDiagnostics);
        } catch (RuntimeException exception) {
            var parseDiagnostics = List.of(FrontendDiagnostic.error(
                    "parse.internal",
                    "Unexpected parser failure: " + exception.getMessage(),
                    sourcePath,
                    null
            ));
            diagnosticManager.reportAll(parseDiagnostics);
            return new FrontendSourceUnit(sourcePath, source, emptySourceFile(), parseDiagnostics);
        }
    }

    private @NotNull SourceFile emptySourceFile() {
        return new SourceFile(
                List.of(),
                new Range(0, 0, new Point(0, 0), new Point(0, 0))
        );
    }
}
