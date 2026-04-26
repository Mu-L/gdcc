package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record ConstructLambdaInsn(@Nullable String resultId, @NotNull String lambdaName,
                                  @NotNull List<Operand> captures) implements ConstructionInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.CONSTRUCT_LAMBDA;
    }

    @Override
    public @NotNull List<Operand> operands() {
        List<Operand> out = new ArrayList<>();
        out.add(new StringOperand(lambdaName));
        out.addAll(captures);
        return out;
    }
}

