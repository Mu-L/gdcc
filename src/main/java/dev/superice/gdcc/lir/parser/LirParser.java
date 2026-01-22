package dev.superice.gdcc.lir.parser;

import dev.superice.gdcc.lir.LirModule;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;

/// Parser interface for Low IR textual representation.
/// Implementations should parse an IR document from a Reader or from a String.
public interface LirParser {
    /** Parse IR from a Reader and produce a LirModule. Use provided moduleName. */
    @NotNull LirModule parse(@NotNull Reader reader, @NotNull String moduleName) throws Exception;

    /** Convenience helper to parse from a String with explicit module name. */
    @NotNull default LirModule parse(@NotNull String xml, @NotNull String moduleName) throws Exception {
        try (var reader = new java.io.StringReader(xml)) {
            return parse(reader, moduleName);
        }
    }

    /** Parse from a Reader using a default module name '<parsed>'. */
    @NotNull default LirModule parse(@NotNull Reader reader) throws Exception {
        return parse(reader, "<parsed>");
    }

    /** Parse from a file; the module name defaults to the file name without extension. */
    @NotNull default LirModule parse(@NotNull File file) throws Exception {
        var moduleName = file.getName();
        var dot = moduleName.lastIndexOf('.');
        if (dot > 0) moduleName = moduleName.substring(0, dot);
        try (var reader = Files.newBufferedReader(file.toPath())) {
            return parse(reader, moduleName);
        }
    }

    /** Convenience helper to parse from a String with default module name. */
    @NotNull default LirModule parse(@NotNull String xml) throws Exception {
        return parse(xml, "<parsed>");
    }
}
