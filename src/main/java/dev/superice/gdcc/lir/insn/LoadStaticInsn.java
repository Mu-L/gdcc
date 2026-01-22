package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record LoadStaticInsn(@Nullable String resultId, @NotNull String className,
                             @NotNull String staticName) implements LoadStoreInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.LOAD_STATIC;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new StringOperand(className), new StringOperand(staticName));
    }
}

