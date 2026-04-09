package dev.superice.gdcc.lir.validation;

import dev.superice.gdcc.exception.InvalidControlFlowGraphException;
import dev.superice.gdcc.lir.LirBasicBlock;
import dev.superice.gdcc.lir.LirFunctionDef;
import dev.superice.gdcc.lir.insn.GoIfInsn;
import dev.superice.gdcc.lir.insn.GotoInsn;
import dev.superice.gdcc.lir.insn.ReturnInsn;
import dev.superice.gdcc.type.GdIntType;
import dev.superice.gdcc.type.GdVoidType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ControlFlowIntegrityValidatorTest {
    private final ControlFlowIntegrityValidator validator = new ControlFlowIntegrityValidator();

    @Test
    public void validateFunction_acceptsValidBranchTargets() {
        var function = newVoidFunction("branch");

        var entry = new LirBasicBlock("entry");
        entry.setTerminator(new GoIfInsn("cond", "then", "else"));
        var thenBlock = new LirBasicBlock("then");
        thenBlock.setTerminator(new GotoInsn("exit"));
        var elseBlock = new LirBasicBlock("else");
        elseBlock.setTerminator(new GotoInsn("exit"));
        var exit = new LirBasicBlock("exit");
        exit.setTerminator(new ReturnInsn(null));

        function.addBasicBlock(entry);
        function.addBasicBlock(thenBlock);
        function.addBasicBlock(elseBlock);
        function.addBasicBlock(exit);
        function.setEntryBlockId("entry");

        assertDoesNotThrow(() -> validator.validateFunction(function));
    }

    @Test
    public void validateFunction_rejectsMissingEntryBlockIdWhenBlocksExist() {
        var function = newVoidFunction("broken_entry");
        var entry = new LirBasicBlock("entry");
        entry.setTerminator(new ReturnInsn(null));
        function.addBasicBlock(entry);

        var exception = assertThrows(InvalidControlFlowGraphException.class,
                () -> validator.validateFunction(function));

        assertTrue(exception.getMessage().contains("entryBlockId"), exception.getMessage());
    }

    @Test
    public void validateFunction_rejectsMissingSuccessorTarget() {
        var function = newVoidFunction("broken_target");
        var entry = new LirBasicBlock("entry");
        entry.setTerminator(new GotoInsn("missing"));
        function.addBasicBlock(entry);
        function.setEntryBlockId("entry");

        var exception = assertThrows(InvalidControlFlowGraphException.class,
                () -> validator.validateFunction(function));

        assertTrue(exception.getMessage().contains("missing"), exception.getMessage());
    }

    @Test
    public void validateFunction_rejectsNonVoidFinallyReturningUserVariable() {
        var function = newIntFunction("broken_finally_return");
        function.createAndAddVariable("value", GdIntType.INT);

        var entry = new LirBasicBlock("entry");
        entry.setTerminator(new GotoInsn("__finally__"));
        var finallyBlock = new LirBasicBlock("__finally__");
        finallyBlock.setTerminator(new ReturnInsn("value"));
        function.addBasicBlock(entry);
        function.addBasicBlock(finallyBlock);
        function.setEntryBlockId("entry");

        var exception = assertThrows(InvalidControlFlowGraphException.class,
                () -> validator.validateFunction(function));

        assertTrue(exception.getMessage().contains("_return_val"), exception.getMessage());
    }

    @Test
    public void validateFunction_rejectsReturningReturnSlotOutsideFinally() {
        var function = newIntFunction("broken_return_slot_use");

        var entry = new LirBasicBlock("entry");
        entry.setTerminator(new ReturnInsn("_return_val"));
        function.addBasicBlock(entry);
        function.setEntryBlockId("entry");

        var exception = assertThrows(InvalidControlFlowGraphException.class,
                () -> validator.validateFunction(function));

        assertTrue(exception.getMessage().contains("__finally__"), exception.getMessage());
    }

    private static LirFunctionDef newVoidFunction(String name) {
        var function = new LirFunctionDef(name);
        function.setReturnType(GdVoidType.VOID);
        return function;
    }

    private static LirFunctionDef newIntFunction(String name) {
        var function = new LirFunctionDef(name);
        function.setReturnType(GdIntType.INT);
        return function;
    }
}
