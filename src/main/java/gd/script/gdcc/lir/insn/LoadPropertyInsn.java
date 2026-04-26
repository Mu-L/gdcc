package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record LoadPropertyInsn(@Nullable String resultId, @NotNull String propertyName,
                               @NotNull String objectId) implements LoadStoreInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.LOAD_PROPERTY;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new StringOperand(propertyName), new VariableOperand(objectId));
    }
}

