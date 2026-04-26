package gd.script.gdcc.backend.c.gen.insn;

import gd.script.gdcc.backend.c.gen.CBodyBuilder;
import gd.script.gdcc.backend.c.gen.CInsnGen;
import gd.script.gdcc.enums.GdInstruction;
import gd.script.gdcc.lir.insn.AssignInsn;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Objects;

public final class AssignInsnGen implements CInsnGen<AssignInsn> {
    @Override
    public @NotNull EnumSet<GdInstruction> getInsnOpcodes() {
        return EnumSet.of(GdInstruction.ASSIGN);
    }

    @Override
    public void generateCCode(@NotNull CBodyBuilder bodyBuilder) {
        var insn = bodyBuilder.getCurrentInsn(this);
        var resultId = insn.resultId();

        var func = bodyBuilder.func();
        var resultVar = func.getVariableById(Objects.requireNonNull(resultId));
        if (resultVar == null) {
            throw bodyBuilder.invalidInsn("Result variable ID " + resultId + " does not exist");
        }
        var sourceVar = func.getVariableById(insn.sourceId());
        if (sourceVar == null) {
            throw bodyBuilder.invalidInsn("Source variable ID " + insn.sourceId() + " does not exist");
        }

        var target = bodyBuilder.targetOfVar(resultVar);
        bodyBuilder.assignVar(target, bodyBuilder.valueOfVar(sourceVar));
    }
}

