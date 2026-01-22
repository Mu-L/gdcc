package dev.superice.gdcc.lir.insn;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record ConstructDictionaryInsn(@Nullable String resultId, @Nullable String keyClassName,
                                      @Nullable String valueClassName) implements ConstructionInstruction {

    @Override
    public GdInstruction opcode() {
        return GdInstruction.CONSTRUCT_DICTIONARY;
    }

    @Override
    public @NotNull List<Operand> operands() {
        List<Operand> ops = new ArrayList<>();
        if (keyClassName != null) ops.add(new StringOperand(keyClassName));
        if (valueClassName != null) ops.add(new StringOperand(valueClassName));
        return ops;
    }
}

