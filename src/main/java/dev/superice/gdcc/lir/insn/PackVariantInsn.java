package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record PackVariantInsn(@Nullable String resultId, @NotNull String valueId) implements TypeInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.PACK_VARIANT;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new VariableOperand(valueId));
    }
}

