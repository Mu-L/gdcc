package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record StoreStaticInsn(@NotNull String className, @NotNull String staticName,
                              @NotNull String valueId) implements LoadStoreInstruction {

    @Override
    public String resultId() {
        return null;
    }

    @Override
    public GdInstruction opcode() {
        return GdInstruction.STORE_STATIC;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new StringOperand(className), new StringOperand(staticName), new VariableOperand(valueId));
    }
}

