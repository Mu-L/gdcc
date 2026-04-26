package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record VariantSetInsn(@NotNull String variantId, @NotNull String keyId,
                             @NotNull String valueId) implements IndexingInstruction {

    @Override
    public String resultId() {
        return null;
    }

    @Override
    public GdInstruction opcode() {
        return GdInstruction.VARIANT_SET;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(variantId), new VariableOperand(keyId), new VariableOperand(valueId));
    }
}

