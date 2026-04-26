package gd.script.gdcc.lir;

import gd.script.gdcc.lir.insn.GoIfInsn;
import gd.script.gdcc.lir.insn.GotoInsn;
import gd.script.gdcc.lir.insn.LineNumberInsn;
import gd.script.gdcc.lir.insn.NopInsn;
import gd.script.gdcc.lir.insn.ReturnInsn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LirBasicBlockTest {
    @Test
    public void blockApi_tracksTerminatorAndSuccessors() {
        var returnBlock = new LirBasicBlock("return");
        returnBlock.appendNonTerminatorInstruction(new LineNumberInsn(7));
        returnBlock.setTerminator(new ReturnInsn(null));

        var gotoBlock = new LirBasicBlock("goto");
        gotoBlock.appendNonTerminatorInstruction(new NopInsn());
        gotoBlock.setTerminator(new GotoInsn("exit"));

        var branchBlock = new LirBasicBlock("branch");
        branchBlock.appendNonTerminatorInstruction(new LineNumberInsn(9));
        branchBlock.setTerminator(new GoIfInsn("cond", "then", "else"));

        assertAll(
                () -> assertEquals(1, returnBlock.getNonTerminatorInstructions().size()),
                () -> assertInstanceOf(ReturnInsn.class, returnBlock.getTerminator()),
                () -> assertEquals(List.of(), returnBlock.getSuccessorIds()),
                () -> assertEquals(List.of("exit"), gotoBlock.getSuccessorIds()),
                () -> assertEquals(List.of("then", "else"), branchBlock.getSuccessorIds())
        );
    }

    @Test
    public void appendNonTerminatorInstruction_insertsBeforeExistingTerminator() {
        var block = new LirBasicBlock("entry");
        block.appendNonTerminatorInstruction(new LineNumberInsn(1));
        block.setTerminator(new ReturnInsn(null));

        block.appendNonTerminatorInstruction(new NopInsn());

        assertAll(
                () -> assertEquals(3, block.getInstructionCount()),
                () -> assertInstanceOf(LineNumberInsn.class, block.getInstructions().getFirst()),
                () -> assertInstanceOf(NopInsn.class, block.getInstruction(1)),
                () -> assertInstanceOf(ReturnInsn.class, block.getInstruction(2))
        );

        block.clearTerminator();

        assertAll(
                () -> assertFalse(block.hasTerminator()),
                () -> assertEquals(2, block.getInstructionCount())
        );
    }

    @Test
    public void instructionsView_rejectsAppendingOrdinaryInstructionAfterTerminator() {
        var block = new LirBasicBlock("entry");
        block.setTerminator(new ReturnInsn(null));

        var exception = assertThrows(IllegalStateException.class,
                () -> block.appendInstruction(new NopInsn()));

        assertTrue(exception.getMessage().contains("terminator"), exception.getMessage());
    }

    @Test
    public void setTerminator_rejectsSecondTerminator() {
        var block = new LirBasicBlock("entry");
        block.setTerminator(new ReturnInsn(null));

        var exception = assertThrows(IllegalStateException.class,
                () -> block.setTerminator(new GotoInsn("other")));

        assertTrue(exception.getMessage().contains("already has a terminator"), exception.getMessage());
    }

    @Test
    public void constructor_rejectsInstructionSequenceAfterTerminator() {
        var exception = assertThrows(IllegalStateException.class,
                () -> new LirBasicBlock("entry", List.of(new ReturnInsn(null), new NopInsn())));

        assertTrue(exception.getMessage().contains("terminator"), exception.getMessage());
    }
}
