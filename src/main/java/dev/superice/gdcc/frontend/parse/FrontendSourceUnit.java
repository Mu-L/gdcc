package dev.superice.gdcc.frontend.parse;

import dev.superice.gdparser.frontend.ast.SourceFile;
import dev.superice.gdcc.frontend.diagnostic.FrontendDiagnostic;
import dev.superice.gdcc.frontend.diagnostic.FrontendDiagnosticSeverity;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// One parsed source unit: source text + AST + parse diagnostics.
public record FrontendSourceUnit(
        @NotNull Path path,
        @NotNull String source,
        @NotNull SourceFile ast,
        @NotNull List<FrontendDiagnostic> parseDiagnostics
) {
    public FrontendSourceUnit {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(ast, "ast must not be null");
        parseDiagnostics = List.copyOf(Objects.requireNonNull(parseDiagnostics, "parseDiagnostics must not be null"));
    }

    public boolean hasParseErrors() {
        return parseDiagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == FrontendDiagnosticSeverity.ERROR);
    }
}
