package dev.superice.gdcc.lir;

import dev.superice.gdcc.lir.insn.ControlFlowInstruction;
import dev.superice.gdcc.lir.insn.GoIfInsn;
import dev.superice.gdcc.lir.insn.GotoInsn;
import dev.superice.gdcc.lir.insn.ReturnInsn;
import dev.superice.gdcc.lir.parser.SimpleLirBlockInsnSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.IOException;
import java.io.StringWriter;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

public record LirBasicBlock(@NotNull String id, @NotNull List<LirInstruction> instructions) {
    public LirBasicBlock(@NotNull String id, List<LirInstruction> instructions) {
        this.id = Objects.requireNonNull(id);
        this.instructions = wrapInstructions(id, Objects.requireNonNull(instructions));
    }

    public LirBasicBlock(@NotNull String id) {
        this(id, new InstructionList(id, List.of()));
    }

    /// Appends one instruction using lexical order semantics.
    ///
    /// This method is strict: once a terminator is present, no later instruction may be
    /// appended through the sequential path.
    public void appendInstruction(@NotNull LirInstruction instruction) {
        instructionList().appendInstruction(Objects.requireNonNull(instruction));
    }

    /// Appends a non-terminator instruction into the ordinary instruction region.
    ///
    /// If the block already has a terminator, the new instruction is inserted immediately
    /// before that terminator so the block stays structurally valid.
    public void appendNonTerminatorInstruction(@NotNull LirInstruction instruction) {
        instructionList().appendNonTerminatorInstruction(Objects.requireNonNull(instruction));
    }

    public @NotNull @UnmodifiableView List<LirInstruction> getNonTerminatorInstructions() {
        return instructionList().getNonTerminatorInstructions();
    }

    public @NotNull @UnmodifiableView List<LirInstruction> getInstructions() {
        return Collections.unmodifiableList(instructionList());
    }

    public int getInstructionCount() {
        return instructionList().size();
    }

    public @NotNull LirInstruction getInstruction(int index) {
        return instructionList().get(index);
    }

    public @Nullable ControlFlowInstruction getTerminator() {
        return instructionList().getTerminator();
    }

    public boolean hasTerminator() {
        return getTerminator() != null;
    }

    public void setTerminator(@NotNull ControlFlowInstruction terminator) {
        instructionList().setTerminator(Objects.requireNonNull(terminator));
    }

    public void clearTerminator() {
        instructionList().clearTerminator();
    }

    public @NotNull List<String> getSuccessorIds() {
        var terminator = getTerminator();
        return switch (terminator) {
            case null -> List.of();
            case ReturnInsn _ -> List.of();
            case GotoInsn(var targetBbId) -> List.of(targetBbId);
            case GoIfInsn(_, var trueBbId, var falseBbId) -> List.of(trueBbId, falseBbId);
            default ->
                    throw new IllegalStateException("Unsupported control-flow terminator: " + terminator.getClass().getName());
        };
    }

    @Override
    public @NotNull String toString() {
        var serializer = new SimpleLirBlockInsnSerializer();
        var stringWriter = new StringWriter();
        try {
            serializer.serialize(this.instructions, stringWriter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "Block \"" + id + "\":\n" + stringWriter;
    }

    private @NotNull InstructionList instructionList() {
        return (InstructionList) instructions;
    }

    private static @NotNull List<LirInstruction> wrapInstructions(@NotNull String id,
                                                                  @NotNull List<LirInstruction> instructions) {
        if (instructions instanceof InstructionList instructionList) {
            return instructionList;
        }
        return new InstructionList(id, instructions);
    }

    private static final class InstructionList extends AbstractList<LirInstruction> implements RandomAccess {
        private final String blockId;
        private final ArrayList<LirInstruction> nonTerminatorInstructions = new ArrayList<>();
        private final List<LirInstruction> nonTerminatorView = Collections.unmodifiableList(nonTerminatorInstructions);
        private @Nullable ControlFlowInstruction terminator;

        private InstructionList(@NotNull String blockId, @NotNull List<LirInstruction> instructions) {
            this.blockId = blockId;
            for (var instruction : instructions) {
                appendInstruction(instruction);
            }
        }

        private void appendInstruction(@NotNull LirInstruction instruction) {
            if (instruction instanceof ControlFlowInstruction controlFlowInstruction) {
                setTerminator(controlFlowInstruction);
                return;
            }
            if (terminator != null) {
                throw new IllegalStateException("Cannot append a non-terminator after the terminator in block '" + blockId + "'");
            }
            nonTerminatorInstructions.add(instruction);
            modCount++;
        }

        private void appendNonTerminatorInstruction(@NotNull LirInstruction instruction) {
            if (instruction instanceof ControlFlowInstruction) {
                throw new IllegalArgumentException("Use setTerminator() for control-flow instructions in block '" + blockId + "'");
            }
            nonTerminatorInstructions.add(instruction);
            modCount++;
        }

        private @NotNull @UnmodifiableView List<LirInstruction> getNonTerminatorInstructions() {
            return nonTerminatorView;
        }

        private @Nullable ControlFlowInstruction getTerminator() {
            return terminator;
        }

        private void setTerminator(@NotNull ControlFlowInstruction terminator) {
            if (this.terminator != null) {
                throw new IllegalStateException("Block '" + blockId + "' already has a terminator");
            }
            this.terminator = terminator;
            modCount++;
        }

        private void clearTerminator() {
            if (terminator == null) {
                return;
            }
            terminator = null;
            modCount++;
        }

        @Override
        public LirInstruction get(int index) {
            if (index < nonTerminatorInstructions.size()) {
                return nonTerminatorInstructions.get(index);
            }
            if (terminator != null && index == nonTerminatorInstructions.size()) {
                return terminator;
            }
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public int size() {
            return nonTerminatorInstructions.size() + (terminator == null ? 0 : 1);
        }

        @Override
        public void add(int index, @NotNull LirInstruction element) {
            if (index < 0 || index > size()) {
                throw new IndexOutOfBoundsException(index);
            }
            if (element instanceof ControlFlowInstruction controlFlowInstruction) {
                if (terminator != null) {
                    throw new IllegalStateException("Block '" + blockId + "' already has a terminator");
                }
                if (index != size()) {
                    throw new IllegalArgumentException("Control-flow instructions must be appended at the end of block '" + blockId + "'");
                }
                terminator = controlFlowInstruction;
                modCount++;
                return;
            }
            if (terminator != null && index > nonTerminatorInstructions.size()) {
                throw new IllegalStateException("Cannot insert a non-terminator after the terminator in block '" + blockId + "'");
            }
            nonTerminatorInstructions.add(index, element);
            modCount++;
        }

        @Override
        public LirInstruction remove(int index) {
            if (index < nonTerminatorInstructions.size()) {
                modCount++;
                return nonTerminatorInstructions.remove(index);
            }
            if (terminator != null && index == nonTerminatorInstructions.size()) {
                var removed = terminator;
                terminator = null;
                modCount++;
                return removed;
            }
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public void clear() {
            if (nonTerminatorInstructions.isEmpty() && terminator == null) {
                return;
            }
            nonTerminatorInstructions.clear();
            terminator = null;
            modCount++;
        }
    }
}
