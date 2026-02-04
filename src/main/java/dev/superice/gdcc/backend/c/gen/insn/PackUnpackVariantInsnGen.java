package dev.superice.gdcc.backend.c.gen.insn;

import dev.superice.gdcc.backend.c.gen.CGenHelper;
import dev.superice.gdcc.backend.c.gen.CInsnGen;
import dev.superice.gdcc.enums.GdInstruction;
import dev.superice.gdcc.exception.InvalidInsnException;
import dev.superice.gdcc.lir.LirBasicBlock;
import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdcc.lir.LirFunctionDef;
import dev.superice.gdcc.lir.insn.PackVariantInsn;
import dev.superice.gdcc.lir.insn.TypeInstruction;
import dev.superice.gdcc.lir.insn.UnpackVariantInsn;
import dev.superice.gdcc.type.GdVariantType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Objects;

public final class PackUnpackVariantInsnGen implements CInsnGen<TypeInstruction> {
    @Override
    public @NotNull EnumSet<GdInstruction> getInsnOpcodes() {
        return EnumSet.of(GdInstruction.PACK_VARIANT, GdInstruction.UNPACK_VARIANT);
    }

    @Override
    public @NotNull String generateCCode(@NotNull CGenHelper helper,
                                         @NotNull LirClassDef clazz,
                                         @NotNull LirFunctionDef func,
                                         @NotNull LirBasicBlock block,
                                         int insnIndex,
                                         @NotNull TypeInstruction instruction) {
        if (instruction.resultId() == null) {
            throw new InvalidInsnException(func.getName(), block.id(), insnIndex, instruction.opcode().opcode(),
                    "Instruction does not have a result variable ID");
        }
        var resultVar = func.getVariableById(Objects.requireNonNull(instruction.resultId()));
        if (resultVar == null) {
            throw new InvalidInsnException(func.getName(), block.id(), insnIndex, instruction.opcode().opcode(),
                    "Result variable ID '" + instruction.resultId() + "' not found in function");
        }
        if (instruction instanceof UnpackVariantInsn unpackVariantInsn) {
            var variantVar = func.getVariableById(unpackVariantInsn.variantId());
            if (variantVar == null) {
                throw new InvalidInsnException(func.getName(), block.id(), insnIndex, instruction.opcode().opcode(),
                        "Variant variable ID '" + unpackVariantInsn.variantId() + "' not found in function");
            }
            if (!(variantVar.type() instanceof GdVariantType)) {
                throw new InvalidInsnException(func.getName(), block.id(), insnIndex, instruction.opcode().opcode(),
                        "Variant variable ID '" + unpackVariantInsn.variantId() + "' is not of variant type, but " +
                                variantVar.type().getTypeName());
            }
            var sb = new StringBuilder();
            sb.append("$").append(resultVar.id()).append(" = ")
                    .append(helper.renderUnpackFunctionName(resultVar.type()))
                    .append("(");
            if (!variantVar.ref()) {
                sb.append("&");
            }
            sb.append("$").append(variantVar.id()).append(");");
            return sb.toString();
        } else if (instruction instanceof PackVariantInsn packVariantInsn) {
            var valueVar = func.getVariableById(packVariantInsn.valueId());
            if (valueVar == null) {
                throw new InvalidInsnException(func.getName(), block.id(), insnIndex, instruction.opcode().opcode(),
                        "Value variable ID '" + packVariantInsn.valueId() + "' not found in function");
            }
            var sb = new StringBuilder();
            sb.append("$").append(resultVar.id()).append(" = ")
                    .append(helper.renderPackFunctionName(valueVar.type()))
                    .append("(");
            if (valueVar.ref()) {
                sb.append("$").append(valueVar.id());
            } else {
                sb.append(helper.renderVarRef(func, valueVar.id()));
            }
            sb.append(");");
            return sb.toString();
        }
        throw new InvalidInsnException(func.getName(), block.id(), insnIndex, instruction.opcode().opcode(),
                "Unsupported instruction type for PackUnpackVariantInsnGen: " + instruction.getClass().getName());
    }
}
