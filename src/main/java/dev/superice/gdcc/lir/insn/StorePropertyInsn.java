package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record StorePropertyInsn(@NotNull String propertyName, @NotNull String objectId,
                                @NotNull String valueId) implements LoadStoreInstruction {

    @Override
    public String resultId() {
        return null;
    }

    @Override
    public GdInstruction opcode() {
        return GdInstruction.STORE_PROPERTY;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new StringOperand(propertyName), new VariableOperand(objectId), new VariableOperand(valueId));
    }
}

