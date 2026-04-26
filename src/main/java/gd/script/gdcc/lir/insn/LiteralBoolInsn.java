package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record LiteralBoolInsn(@Nullable String resultId, boolean value) implements NewDataInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.LITERAL_BOOL;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new BooleanOperand(value));
    }
}
