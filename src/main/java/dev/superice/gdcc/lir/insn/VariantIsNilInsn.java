package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record VariantIsNilInsn(@Nullable String resultId, @NotNull String variantId) implements TypeInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.VARIANT_IS_NIL;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(variantId));
    }
}

