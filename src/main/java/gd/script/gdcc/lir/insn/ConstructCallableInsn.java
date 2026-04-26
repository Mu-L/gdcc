package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ConstructCallableInsn(@Nullable String resultId,
                                    @NotNull String functionName) implements ConstructionInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.CONSTRUCT_CALLABLE;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new StringOperand(functionName));
    }
}

