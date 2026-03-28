package dev.superice.gdcc.lir.validation;

import dev.superice.gdcc.exception.InvalidControlFlowGraphException;
import dev.superice.gdcc.lir.LirBasicBlock;
import dev.superice.gdcc.lir.LirFunctionDef;
import dev.superice.gdcc.lir.insn.ControlFlowInstruction;
import org.jetbrains.annotations.NotNull;

/// Validates block-local terminator rules and function-level successor integrity.
public final class ControlFlowIntegrityValidator {
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

    private static @NotNull InvalidControlFlowGraphException invalid(@NotNull LirFunctionDef func,
                                                                     @NotNull LirBasicBlock block,
                                                                     int index,
                                                                     @NotNull dev.superice.gdcc.lir.LirInstruction instruction,
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
