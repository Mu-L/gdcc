package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
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
