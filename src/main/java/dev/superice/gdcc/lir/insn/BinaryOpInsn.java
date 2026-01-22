package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.enums.GodotOperator;
import dev.superice.gdcc.lir.LirInstruction;
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

