package gd.script.gdcc.frontend.lowering.pass.body;

import gd.script.gdcc.frontend.lowering.FrontendSubscriptAccessSupport;
import gd.script.gdcc.lir.LirBasicBlock;
import gd.script.gdcc.lir.insn.VariantGetIndexedInsn;
import gd.script.gdcc.lir.insn.VariantGetInsn;
import gd.script.gdcc.lir.insn.VariantGetKeyedInsn;
import gd.script.gdcc.lir.insn.VariantGetNamedInsn;
import gd.script.gdcc.lir.insn.VariantSetIndexedInsn;
import gd.script.gdcc.lir.insn.VariantSetInsn;
import gd.script.gdcc.lir.insn.VariantSetKeyedInsn;
import gd.script.gdcc.lir.insn.VariantSetNamedInsn;
import gd.script.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;

public final class FrontendSubscriptInsnSupport {
    private FrontendSubscriptInsnSupport() {
    }

    static void appendLoad(
            @NotNull LirBasicBlock block,
            @NotNull String resultSlotId,
            @NotNull String receiverSlotId,
            @NotNull GdType receiverType,
            @NotNull String keySlotId,
            @NotNull GdType keyType
    ) {
        appendLoad(
                block,
                resultSlotId,
                receiverSlotId,
                keySlotId,
                FrontendSubscriptAccessSupport.determineAccessKind(receiverType, keyType)
        );
    }

    static void appendLoad(
            @NotNull LirBasicBlock block,
            @NotNull String resultSlotId,
            @NotNull String receiverSlotId,
            @NotNull String keySlotId,
            @NotNull FrontendSubscriptAccessSupport.AccessKind accessKind
    ) {
        switch (accessKind) {
            case GENERIC -> block.appendNonTerminatorInstruction(new VariantGetInsn(
                    resultSlotId,
                    receiverSlotId,
                    keySlotId
            ));
            case KEYED -> block.appendNonTerminatorInstruction(new VariantGetKeyedInsn(
                    resultSlotId,
                    receiverSlotId,
                    keySlotId
            ));
            case NAMED -> block.appendNonTerminatorInstruction(new VariantGetNamedInsn(
                    resultSlotId,
                    receiverSlotId,
                    keySlotId
            ));
            case INDEXED -> block.appendNonTerminatorInstruction(new VariantGetIndexedInsn(
                    resultSlotId,
                    receiverSlotId,
                    keySlotId
            ));
        }
    }

    static void appendStore(
            @NotNull LirBasicBlock block,
            @NotNull String receiverSlotId,
            @NotNull GdType receiverType,
            @NotNull String keySlotId,
            @NotNull GdType keyType,
            @NotNull String rhsSlotId
    ) {
        appendStore(
                block,
                receiverSlotId,
                keySlotId,
                rhsSlotId,
                FrontendSubscriptAccessSupport.determineAccessKind(receiverType, keyType)
        );
    }

    static void appendStore(
            @NotNull LirBasicBlock block,
            @NotNull String receiverSlotId,
            @NotNull String keySlotId,
            @NotNull String rhsSlotId,
            @NotNull FrontendSubscriptAccessSupport.AccessKind accessKind
    ) {
        switch (accessKind) {
            case GENERIC -> block.appendNonTerminatorInstruction(new VariantSetInsn(
                    receiverSlotId,
                    keySlotId,
                    rhsSlotId
            ));
            case KEYED -> block.appendNonTerminatorInstruction(new VariantSetKeyedInsn(
                    receiverSlotId,
                    keySlotId,
                    rhsSlotId
            ));
            case NAMED -> block.appendNonTerminatorInstruction(new VariantSetNamedInsn(
                    receiverSlotId,
                    keySlotId,
                    rhsSlotId
            ));
            case INDEXED -> block.appendNonTerminatorInstruction(new VariantSetIndexedInsn(
                    receiverSlotId,
                    keySlotId,
                    rhsSlotId
            ));
        }
    }

}
