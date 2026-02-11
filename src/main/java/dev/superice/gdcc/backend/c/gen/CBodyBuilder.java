package dev.superice.gdcc.backend.c.gen;

import dev.superice.gdcc.exception.InvalidInsnException;
import dev.superice.gdcc.lir.LirBasicBlock;
import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdcc.lir.LirFunctionDef;
import dev.superice.gdcc.lir.LirInstruction;
import dev.superice.gdcc.lir.LirVariable;
import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// Builder for generating C function body code.
///
/// This builder is created per function and used on a single thread.
/// It tracks current instruction position to provide precise codegen errors.
@SuppressWarnings("UnusedReturnValue")
public final class CBodyBuilder {
    private final @NotNull CGenHelper helper;
    private final @NotNull LirClassDef clazz;
    private final @NotNull LirFunctionDef func;
    private final @NotNull StringBuilder out = new StringBuilder();

    private @Nullable LirBasicBlock currentBlock;
    private int currentInsnIndex = -1;
    private @Nullable LirInstruction currentInsn;

    private int tempVarCounter = 0;

    public CBodyBuilder(@NotNull CGenHelper helper,
                        @NotNull LirClassDef clazz,
                        @NotNull LirFunctionDef func) {
        this.helper = Objects.requireNonNull(helper);
        this.clazz = Objects.requireNonNull(clazz);
        this.func = Objects.requireNonNull(func);
    }

    public @NotNull CBodyBuilder setCurrentPosition(@NotNull LirBasicBlock block,
                                                    int insnIndex,
                                                    @NotNull LirInstruction instruction) {
        this.currentBlock = Objects.requireNonNull(block);
        this.currentInsnIndex = insnIndex;
        this.currentInsn = Objects.requireNonNull(instruction);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <Insn extends LirInstruction> @NotNull Insn getCurrentInsn(@NotNull CInsnGen<Insn> gen) {
        Objects.requireNonNull(gen);
        var insn = currentInsn;
        if (insn == null || currentBlock == null) {
            throw new IllegalStateException("Current instruction position is not set");
        }
        if (!gen.getInsnOpcodes().contains(insn.opcode())) {
            throw new InvalidInsnException(
                    func.getName(),
                    currentBlock.id(),
                    currentInsnIndex,
                    insn.opcode().opcode(),
                    "Current instruction opcode '" + insn.opcode().opcode() +
                            "' is not handled by generator '" + gen.getClass().getSimpleName() + "'"
            );
        }
        return (Insn) insn;
    }

    public @NotNull CBodyBuilder beginBasicBlock(@NotNull String blockId) {
        out.append(blockId).append(": // ").append(blockId).append("\n");
        return this;
    }

    public @NotNull CBodyBuilder appendLine(@NotNull String line) {
        out.append(line).append("\n");
        return this;
    }

    public @NotNull CBodyBuilder appendRaw(@NotNull String code) {
        out.append(code);
        return this;
    }

    public @NotNull String newTempName(@NotNull String prefix) {
        return "__gdcc_tmp_" + prefix + "_" + tempVarCounter++;
    }

    public @NotNull InvalidInsnException invalidInsn(@NotNull String reason) {
        var insn = currentInsn;
        var block = currentBlock;
        if (insn == null || block == null) {
            return new InvalidInsnException("Invalid instruction in function '" + func.getName() + "': " + reason);
        }
        return new InvalidInsnException(func.getName(), block.id(), currentInsnIndex, insn.opcode().opcode(), reason);
    }

    public @NotNull CGenHelper helper() {
        return helper;
    }

    public @NotNull ClassRegistry classRegistry() {
        return helper.context().classRegistry();
    }

    public @NotNull LirClassDef clazz() {
        return clazz;
    }

    public @NotNull LirFunctionDef func() {
        return func;
    }

    public @Nullable LirBasicBlock currentBlock() {
        return currentBlock;
    }

    public int currentInsnIndex() {
        return currentInsnIndex;
    }

    public @Nullable LirInstruction currentInsn() {
        return currentInsn;
    }

    public @NotNull String build() {
        return out.toString();
    }

    /// Creates a value reference from a variable.
    public @NotNull ValueRef valueOfVar(@NotNull LirVariable variable) {
        return new VarValue(variable);
    }

    /// Creates a value reference from a raw C expression and type.
    public @NotNull ValueRef valueOfExpr(@NotNull String code, @NotNull GdType type) {
        return new ExprValue(code, type);
    }

    /// Creates a target reference from a variable.
    ///
    /// Throws InvalidInsnException if the variable is a reference variable (ref=true).
    public @NotNull TargetRef targetOfVar(@NotNull LirVariable variable) {
        if (variable.ref()) {
            throw invalidInsn("Cannot assign to reference variable '" + variable.id() + "'");
        }
        return new TargetRef(variable);
    }

    public @NotNull CBodyBuilder assignVar(@NotNull TargetRef target, @NotNull ValueRef value) {
        // TODO: Phase C - Implement full assignment semantics (destroy old, copy new, own/release)
        // For Phase B, we just generate simple assignment
        out.append(target.generateCode()).append(" = ").append(value.generateCode()).append(";\n");
        return this;
    }

    public @NotNull CBodyBuilder callVoid(@NotNull String funcName, @NotNull List<ValueRef> args) {
        out.append(funcName).append("(");
        generateArgs(args);
        out.append(");\n");
        return this;
    }

    public @NotNull CBodyBuilder callAssign(@NotNull TargetRef target, @NotNull String funcName, @NotNull List<ValueRef> args) {
        // TODO: Phase C - Implement full assignment semantics
        out.append(target.generateCode()).append(" = ").append(funcName).append("(");
        generateArgs(args);
        out.append(");\n");
        return this;
    }

    public @NotNull CBodyBuilder jump(@NotNull String blockId) {
        out.append("goto ").append(blockId).append(";\n");
        return this;
    }

    public @NotNull CBodyBuilder jumpIf(@NotNull ValueRef condition, @NotNull String trueBlockId, @NotNull String falseBlockId) {
        out.append("if (").append(condition.generateCode()).append(") goto ").append(trueBlockId).append(";\n");
        out.append("else goto ").append(falseBlockId).append(";\n");
        return this;
    }

    public @NotNull CBodyBuilder returnVoid() {
        out.append("return;\n");
        return this;
    }

    public @NotNull CBodyBuilder returnValue(@NotNull ValueRef value) {
        // TODO: Phase C - Implement copy/return semantics
        out.append("return ").append(value.generateCode()).append(";\n");
        return this;
    }

    private void generateArgs(@NotNull List<ValueRef> args) {
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            // TODO: Phase C - Use helper.renderValueRef or similar logic for complex types
            out.append(args.get(i).generateCode());
        }
    }

    public sealed interface ValueRef permits VarValue, ExprValue {
        @NotNull GdType type();

        @NotNull String generateCode();
    }

    public record VarValue(@NotNull LirVariable variable) implements ValueRef {
        public VarValue {
            Objects.requireNonNull(variable);
        }

        @Override
        public @NotNull GdType type() {
            return variable.type();
        }

        @Override
        public @NotNull String generateCode() {
            return "$" + variable.id();
        }
    }

    public record ExprValue(@NotNull String code, @NotNull GdType type) implements ValueRef {
        public ExprValue {
            Objects.requireNonNull(code);
            Objects.requireNonNull(type);
        }

        @Override
        public @NotNull GdType type() {
            return type;
        }

        @Override
        public @NotNull String generateCode() {
            return code;
        }
    }

    public record TargetRef(@NotNull LirVariable variable) {
        public TargetRef {
            Objects.requireNonNull(variable);
        }

        public @NotNull GdType type() {
            return variable.type();
        }

        public @NotNull String generateCode() {
            return "$" + variable.id();
        }
    }
}
