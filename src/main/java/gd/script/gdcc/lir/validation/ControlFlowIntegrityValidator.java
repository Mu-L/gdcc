package gd.script.gdcc.lir.validation;

import gd.script.gdcc.exception.InvalidControlFlowGraphException;
import gd.script.gdcc.lir.LirBasicBlock;
import gd.script.gdcc.lir.LirFunctionDef;
import gd.script.gdcc.lir.LirInstruction;
import gd.script.gdcc.lir.insn.ControlFlowInstruction;
import gd.script.gdcc.lir.insn.ReturnInsn;
import gd.script.gdcc.type.GdVoidType;
import org.jetbrains.annotations.NotNull;

/// Validates block-local terminator rules and function-level successor integrity.
public final class ControlFlowIntegrityValidator {
    private static final String RETURN_SLOT_ID = "_return_val";

    public void validateFunction(@NotNull LirFunctionDef func) {
        validateEntryBlock(func);
        for (var block : func) {
            validateBlock(func, block);
        }
    }

    private void validateEntryBlock(@NotNull LirFunctionDef func) {
        if (func.getBasicBlockCount() == 0) {
            return;
        }
        if (func.getEntryBlockId().isEmpty()) {
            throw new InvalidControlFlowGraphException(func.getName(),
                    "basic blocks exist but entryBlockId is not set");
        }
        if (!func.hasBasicBlock(func.getEntryBlockId())) {
            throw new InvalidControlFlowGraphException(func.getName(),
                    "entryBlockId '" + func.getEntryBlockId() + "' does not reference an existing block");
        }
    }

    private void validateBlock(@NotNull LirFunctionDef func, @NotNull LirBasicBlock block) {
        var instructions = block.getInstructions();
        var sawTerminator = false;
        for (int index = 0; index < instructions.size(); index++) {
            var instruction = instructions.get(index);
            if (!(instruction instanceof ControlFlowInstruction controlFlowInstruction)) {
                if (sawTerminator) {
                    throw invalid(func, block, index, instruction,
                            "non-terminator appears after a control-flow terminator");
                }
                continue;
            }
            if (sawTerminator) {
                throw invalid(func, block, index, instruction,
                        "multiple control-flow terminators appear in one block");
            }
            sawTerminator = true;
            if (index != instructions.size() - 1) {
                throw invalid(func, block, index, instruction,
                        "control-flow terminator must be the last instruction in the block");
            }
            validateSuccessorTargets(func, block, index, controlFlowInstruction);
            if (controlFlowInstruction instanceof ReturnInsn returnInsn) {
                validateReturnSurface(func, block, index, returnInsn);
            }
        }
    }

    private void validateSuccessorTargets(@NotNull LirFunctionDef func,
                                          @NotNull LirBasicBlock block,
                                          int index,
                                          @NotNull ControlFlowInstruction instruction) {
        for (var successorId : block.getSuccessorIds()) {
            if (!func.hasBasicBlock(successorId)) {
                throw invalid(func, block, index, instruction,
                        "successor block '" + successorId + "' does not exist");
            }
        }
    }

    /// Non-void functions publish through `_return_val`.
    /// On the LIR surface, the only valid `__finally__` terminator is `ReturnInsn("_return_val")`;
    /// direct `ReturnInsn(<user-var>)` in `__finally__` would bypass the publish slot and drift
    /// away from the backend ownership contract.
    private void validateReturnSurface(@NotNull LirFunctionDef func,
                                       @NotNull LirBasicBlock block,
                                       int index,
                                       @NotNull ReturnInsn returnInsn) {
        var returnValueId = returnInsn.returnValueId();
        if ("__finally__".equals(block.id())) {
            if (func.getReturnType() instanceof GdVoidType) {
                if (returnValueId != null) {
                    throw invalid(func, block, index, returnInsn,
                            "void __finally__ block must end with bare return");
                }
                return;
            }
            if (!RETURN_SLOT_ID.equals(returnValueId)) {
                throw invalid(func, block, index, returnInsn,
                        "non-void __finally__ block must return " + RETURN_SLOT_ID);
            }
            return;
        }

        if (RETURN_SLOT_ID.equals(returnValueId)) {
            throw invalid(func, block, index, returnInsn,
                    RETURN_SLOT_ID + " sentinel can only be returned from __finally__");
        }
    }

    private static @NotNull InvalidControlFlowGraphException invalid(@NotNull LirFunctionDef func,
                                                                     @NotNull LirBasicBlock block,
                                                                     int index,
                                                                     @NotNull LirInstruction instruction,
                                                                     @NotNull String reason) {
        return new InvalidControlFlowGraphException(
                func.getName(),
                block.id(),
                index,
                instruction.opcode().opcode(),
                reason
        );
    }
}
