package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import gd.script.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class NopInsn implements MiscInstruction {
    public NopInsn() {
    }

    @Override
    public @Nullable String resultId() {
        return null;
    }

    @Override
    public GdInstruction opcode() {
        return GdInstruction.NOP;
    }

    @Override
    public @NotNull List<LirInstruction.Operand> operands() {
        return List.of();
    }
}

