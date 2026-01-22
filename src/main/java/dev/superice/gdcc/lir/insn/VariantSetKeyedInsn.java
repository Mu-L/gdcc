package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record VariantSetKeyedInsn(@NotNull String keyedVariantId, @NotNull String keyId,
                                  @NotNull String valueId) implements IndexingInstruction {

    @Override
    public String resultId() {
        return null;
    }

    @Override
    public GdInstruction opcode() {
        return GdInstruction.VARIANT_SET_KEYED;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(keyedVariantId), new VariableOperand(keyId), new VariableOperand(valueId));
    }
}

