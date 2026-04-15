package dev.superice.gdcc.frontend.lowering.pass.body;

import dev.superice.gdcc.frontend.lowering.FrontendSubscriptAccessSupport;
import dev.superice.gdcc.lir.LirBasicBlock;
import dev.superice.gdcc.lir.insn.VariantGetIndexedInsn;
import dev.superice.gdcc.lir.insn.VariantGetInsn;
import dev.superice.gdcc.lir.insn.VariantGetKeyedInsn;
import dev.superice.gdcc.lir.insn.VariantGetNamedInsn;
import dev.superice.gdcc.lir.insn.VariantSetIndexedInsn;
import dev.superice.gdcc.lir.insn.VariantSetInsn;
import dev.superice.gdcc.lir.insn.VariantSetKeyedInsn;
import dev.superice.gdcc.lir.insn.VariantSetNamedInsn;
import dev.superice.gdcc.type.GdType;
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
