package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import gd.script.gdcc.enums.GodotOperator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record BinaryOpInsn(@Nullable String resultId, @NotNull GodotOperator op, @NotNull String leftId,
                           @NotNull String rightId) implements ConstructionInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.BINARY_OP;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new GdOperatorOperand(op), new VariableOperand(leftId), new VariableOperand(rightId));
    }
}

