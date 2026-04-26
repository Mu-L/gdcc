package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record GoIfInsn(@NotNull String conditionVarId, @NotNull String trueBbId,
                       @NotNull String falseBbId) implements ControlFlowInstruction {

    @Override
    public @Nullable String resultId() {
        return null;
    }

    @Override
    public GdInstruction opcode() {
        return GdInstruction.GO_IF;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(conditionVarId), new BasicBlockOperand(trueBbId), new BasicBlockOperand(falseBbId));
    }
}

