package dev.superice.gdcc.backend.c.gen;

import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.lir.LirBasicBlock;
import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdcc.lir.LirFunctionDef;
import dev.superice.gdcc.lir.LirInstruction;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public interface CInsnGen<Insn extends LirInstruction> {
    @NotNull EnumSet<GdInstruction> getInsnOpcodes();

    default @NotNull String generateCCode(@NotNull CGenHelper helper,
                                  @NotNull LirClassDef clazz,
                                  @NotNull LirFunctionDef func,
                                  @NotNull LirBasicBlock block,
                                  int insnIndex,
                                  @NotNull Insn instruction) {
        return "";
    }

    default @NotNull String generateCCode(@NotNull CBodyBuilder bodyBuilder) {
        var insn = bodyBuilder.getCurrentInsn(this);
        var block = bodyBuilder.currentBlock();
        if (block == null) {
            throw new IllegalStateException("Current basic block is not set");
        }
        return generateCCode(
                bodyBuilder.helper(),
                bodyBuilder.clazz(),
                bodyBuilder.func(),
                block,
                bodyBuilder.currentInsnIndex(),
                insn
        );
    }
}
