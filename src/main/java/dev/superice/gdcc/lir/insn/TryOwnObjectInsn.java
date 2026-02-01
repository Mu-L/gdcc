package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record TryOwnObjectInsn(@NotNull String objectId) implements ConstructionInstruction {
    @Override
    @Nullable public String resultId() {
        return null;
    }

    @Override
    public GdInstruction opcode() {
        return GdInstruction.TRY_OWN_OBJECT;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(objectId));
    }
}
