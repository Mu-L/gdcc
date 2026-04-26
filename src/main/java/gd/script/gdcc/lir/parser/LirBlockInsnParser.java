package gd.script.gdcc.lir.parser;

import gd.script.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/// Interface for parsing LIR block instructions.
public interface LirBlockInsnParser {
    @NotNull List<LirInstruction> parse(@NotNull Reader reader);

    @NotNull default List<LirInstruction> parse(@NotNull String xml) {
        try (var reader = new StringReader(xml)) {
            return parse(reader);
        }
    }
}
