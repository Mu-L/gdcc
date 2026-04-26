package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.GdInstruction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record LineNumberInsn(int lineNumber) implements MiscInstruction {

    @Override
    public String resultId() {
        return null;
    }

    @Override
    public GdInstruction opcode() {
        return GdInstruction.LINE_NUMBER;
    }

    @Override
    public @NotNull List<Operand> operands() {
        return List.of(new IntOperand(lineNumber));
    }
}

