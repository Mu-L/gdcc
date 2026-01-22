package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ConstructBuiltinInsn(@Nullable String resultId,
                                   @NotNull List<Operand> args) implements ConstructionInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.CONSTRUCT_BUILTIN;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return args;
    }
}
