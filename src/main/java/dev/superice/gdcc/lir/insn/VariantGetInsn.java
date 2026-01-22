package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record VariantGetInsn(@Nullable String resultId, @NotNull String variantId,
                             @NotNull String keyId) implements IndexingInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.VARIANT_GET;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(variantId), new VariableOperand(keyId));
    }
}
