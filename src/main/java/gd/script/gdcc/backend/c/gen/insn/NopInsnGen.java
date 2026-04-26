package gd.script.gdcc.backend.c.gen.insn;

import gd.script.gdcc.backend.c.gen.CGenHelper;
import gd.script.gdcc.backend.c.gen.CInsnGen;
import gd.script.gdcc.enums.GdInstruction;
import gd.script.gdcc.lir.LirBasicBlock;
import gd.script.gdcc.lir.LirClassDef;
import gd.script.gdcc.lir.LirFunctionDef;
import gd.script.gdcc.lir.insn.NopInsn;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public final class NopInsnGen implements CInsnGen<NopInsn> {
    @Override
    public @NotNull EnumSet<GdInstruction> getInsnOpcodes() {
        return EnumSet.of(GdInstruction.NOP);
    }

    @Override
    public @NotNull String generateCCode(@NotNull CGenHelper helper, @NotNull LirClassDef clazz, @NotNull LirFunctionDef func, @NotNull LirBasicBlock block, int insnIndex, @NotNull NopInsn instruction) {
        return "";
    }
}
