package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record CallIntrinsicInsn(@Nullable String resultId, @NotNull String intrinsicName,
                                @NotNull List<Operand> args) implements CallInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.CALL_INTRINSIC;
    }

    @Override
    public @NotNull List<Operand> operands() {
        List<Operand> out = new ArrayList<>();
        out.add(new StringOperand(intrinsicName));
        out.addAll(args);
        return out;
    }
}

