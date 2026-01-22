package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record CallStaticMethodInsn(@Nullable String resultId, @NotNull String className, @NotNull String methodName,
                                   @NotNull List<Operand> args) implements CallInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.CALL_STATIC_METHOD;
    }

    @Override
    public @NotNull List<Operand> operands() {
        List<Operand> out = new ArrayList<>();
        out.add(new StringOperand(className));
        out.add(new StringOperand(methodName));
        out.addAll(args);
        return out;
    }
}

