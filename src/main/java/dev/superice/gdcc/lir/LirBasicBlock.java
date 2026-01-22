package dev.superice.gdcc.lir;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record LirBasicBlock(@NotNull String id, @NotNull List<LirInstruction> instructions) {
    public LirBasicBlock(String id, List<LirInstruction> instructions) {
        this.id = Objects.requireNonNull(id);
        this.instructions = new ArrayList<>(Objects.requireNonNull(instructions));
    }

    public LirBasicBlock(@NotNull String id) {
        this(id, new ArrayList<>());
    }
}
