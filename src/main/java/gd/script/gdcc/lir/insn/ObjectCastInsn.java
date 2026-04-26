package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ObjectCastInsn(@Nullable String resultId, @NotNull String className,
                             @NotNull String objectId) implements TypeInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.OBJECT_CAST;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new StringOperand(className), new VariableOperand(objectId));
    }
}

