package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record VariantGetKeyedInsn(@Nullable String resultId, @NotNull String keyedVariantId,
                                  @NotNull String keyId) implements IndexingInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.VARIANT_GET_KEYED;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(keyedVariantId), new VariableOperand(keyId));
    }
}

