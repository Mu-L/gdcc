package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record GotoInsn(@NotNull String targetBbId) implements ControlFlowInstruction {

    @Override
    public @Nullable String resultId() {
        return null;
    }

    @Override
    public GdInstruction opcode() {
        return GdInstruction.GOTO;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new BasicBlockOperand(targetBbId));
    }
}
