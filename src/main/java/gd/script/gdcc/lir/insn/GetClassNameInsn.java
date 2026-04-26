package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record GetClassNameInsn(@Nullable String resultId, @NotNull String id) implements TypeInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.GET_CLASS_NAME;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(id));
    }
}

