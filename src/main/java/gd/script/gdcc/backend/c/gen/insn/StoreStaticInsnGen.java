package gd.script.gdcc.backend.c.gen.insn;

import gd.script.gdcc.backend.c.gen.CBodyBuilder;
import gd.script.gdcc.backend.c.gen.CInsnGen;
import gd.script.gdcc.enums.GdInstruction;
import gd.script.gdcc.lir.insn.StoreStaticInsn;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public final class StoreStaticInsnGen implements CInsnGen<StoreStaticInsn> {
    @Override
    public @NotNull EnumSet<GdInstruction> getInsnOpcodes() {
        return EnumSet.of(GdInstruction.STORE_STATIC);
    }

    @Override
    public void generateCCode(@NotNull CBodyBuilder bodyBuilder) {
        throw bodyBuilder.invalidInsn(
                "Unsupported static store: 'store_static' is not allowed in current backend"
        );
    }
}
