package gd.script.gdcc.backend.c.gen;

import gd.script.gdcc.enums.GdInstruction;
import gd.script.gdcc.lir.LirBasicBlock;
import gd.script.gdcc.lir.LirClassDef;
import gd.script.gdcc.lir.LirFunctionDef;
import gd.script.gdcc.lir.LirInstruction;
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

    default void generateCCode(@NotNull CBodyBuilder bodyBuilder) {
        var insn = bodyBuilder.getCurrentInsn(this);
        var block = bodyBuilder.currentBlock();
        if (block == null) {
            throw new IllegalStateException("Current basic block is not set");
        }
        var generated = generateCCode(
                bodyBuilder.helper(),
                bodyBuilder.clazz(),
                bodyBuilder.func(),
                block,
                bodyBuilder.currentInsnIndex(),
                insn
        );
        if (!generated.isEmpty()) {
            bodyBuilder.appendLine(generated);
        }
    }
}
