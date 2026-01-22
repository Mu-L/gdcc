package dev.superice.gdcc.lir.parser;

import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/// Interface for serializing LIR block instructions.
public interface LirBlockInsnSerializer {
    void serialize(@NotNull List<LirInstruction> insnList, @NotNull Writer writer) throws IOException;
}
