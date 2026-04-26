package gd.script.gdcc.lir.insn;

import gd.script.gdcc.enums.LifecycleProvenance;
import org.jetbrains.annotations.NotNull;

public interface LifecycleInstruction extends ConstructionInstruction {
    @NotNull LifecycleProvenance getProvenance();
}
