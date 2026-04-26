package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ReturnInsn(@Nullable String returnValueId) implements ControlFlowInstruction {

    @Override
    public @Nullable String resultId() {
        return null;
    }

    @Override
    public GdInstruction opcode() {
        return GdInstruction.RETURN;
    }

    @Override
    public @NotNull List<Operand> operands() {
        if (returnValueId == null) return List.of();
        return List.of(new VariableOperand(returnValueId));
    }
}

