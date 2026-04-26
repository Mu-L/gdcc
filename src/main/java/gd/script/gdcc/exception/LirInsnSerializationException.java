package gd.script.gdcc.exception;

import gd.script.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;

public class LirInsnSerializationException extends GdccException {
    public final int insnIndex;
    public final @NotNull LirInstruction instruction;
    public final @NotNull String reason;

    public LirInsnSerializationException(int insnIndex, @NotNull LirInstruction instruction, @NotNull String reason) {
        super("Failed to serialize LIR instruction " + instruction.opcode().name() + " at index " + insnIndex + ": " + reason);
        this.insnIndex = insnIndex;
        this.instruction = instruction;
        this.reason = reason;
    }
}
