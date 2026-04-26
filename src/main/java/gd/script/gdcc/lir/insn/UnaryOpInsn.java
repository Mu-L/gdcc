package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import gd.script.gdcc.enums.GodotOperator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record UnaryOpInsn(@Nullable String resultId, @NotNull GodotOperator op,
                          @NotNull String operandId) implements ConstructionInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.UNARY_OP;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new GdOperatorOperand(op), new VariableOperand(operandId));
    }
}

