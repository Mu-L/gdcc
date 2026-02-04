package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record PackVariantInsn(@NotNull String resultId, @NotNull String valueId) implements TypeInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.PACK_VARIANT;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(valueId));
    }
}

