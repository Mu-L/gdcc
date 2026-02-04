package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record UnpackVariantInsn(@NotNull String resultId, @NotNull String variantId) implements TypeInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.UNPACK_VARIANT;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(variantId));
    }
}

