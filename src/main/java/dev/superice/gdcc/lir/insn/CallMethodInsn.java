package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record CallMethodInsn(@Nullable String resultId, @NotNull String methodName, @NotNull String objectId,
                             @NotNull List<Operand> args) implements CallInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.CALL_METHOD;
    }

    @Override
    public @NotNull List<Operand> operands() {
        List<Operand> out = new ArrayList<>();
        out.add(new StringOperand(methodName));
        out.add(new VariableOperand(objectId));
        out.addAll(args);
        return out;
    }
}

