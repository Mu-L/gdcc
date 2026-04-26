package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ConstructArrayInsn(@Nullable String resultId,
                                 @Nullable String className) implements ConstructionInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.CONSTRUCT_ARRAY;
    }

    @Override
    public @NotNull List<Operand> operands() {
        if (className == null) return List.of();
        return List.of(new StringOperand(className));
    }
}

