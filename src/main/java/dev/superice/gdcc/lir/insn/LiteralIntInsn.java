package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record LiteralIntInsn(@Nullable String resultId, int value) implements NewDataInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.LITERAL_INT;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new IntOperand(value));
    }
}

