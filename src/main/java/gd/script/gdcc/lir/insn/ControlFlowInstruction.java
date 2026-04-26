package gd.script.gdcc.lir.insn;

import gd.script.gdcc.lir.LirInstruction;

public interface ControlFlowInstruction extends LirInstruction {
    default ReturnInsn getAsReturnInsn() {
        return (ReturnInsn) this;
    }

    default GotoInsn getAsGotoInsn() {
        return (GotoInsn) this;
    }

    default GoIfInsn getAsGoIfInsn() {
        return (GoIfInsn) this;
    }
}
