package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record VariantGetIndexedInsn(@Nullable String resultId, @NotNull String variantId,
                                    int index) implements IndexingInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.VARIANT_GET_INDEXED;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(variantId), new IntOperand(index));
    }
}

