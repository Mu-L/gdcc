package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record IsInstanceOfInsn(@Nullable String resultId, @NotNull String className,
                               @NotNull String objectId) implements TypeInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.IS_INSTANCE_OF;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new StringOperand(className), new VariableOperand(objectId));
    }
}

