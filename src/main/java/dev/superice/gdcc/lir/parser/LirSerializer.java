package dev.superice.gdcc.lir.parser;

import dev.superice.gdcc.lir.LirModule;
import org.jetbrains.annotations.NotNull;

import java.io.Writer;

/// Serializer interface for LIR objects to XML. Implementations may provide
/// optimized serializers.
public interface LirSerializer {
    /** Serialize a LirModule into XML and write to the provided Writer. */
    void serialize(@NotNull LirModule module, @NotNull Writer out) throws Exception;

    /** Convenience: serialize to String. */
    @NotNull default String serializeToString(@NotNull LirModule module) throws Exception {
        try (var sw = new java.io.StringWriter()) {
            serialize(module, sw);
            return sw.toString();
        }
    }
}
