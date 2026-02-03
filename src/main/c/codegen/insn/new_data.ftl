<#-- @ftlvariable name="insn" type="dev.superice.gdcc.lir.insn.NewDataInstruction" -->
<#-- @ftlvariable name="func" type="dev.superice.gdcc.lir.LirFunctionDef" -->
<#-- @ftlvariable name="helper" type="dev.superice.gdcc.backend.c.gen.CGenHelper" -->
<#switch insn.opcode().opcode()>
    <#case "literal_string_name">
        <#assign literalStringNameInsn = insn.getAsLiteralStringNameInsn()>
        <#if func.checkVariableRef(literalStringNameInsn.resultId())>
            godot_string_name_new_with_utf8_chars($${literalStringNameInsn.resultId()}, u8"${literalStringNameInsn.value()}");
        <#else>
            $${literalStringNameInsn.resultId()} = godot_new_StringName_with_utf8_chars(u8"${literalStringNameInsn.value()}");
        </#if>
        <#break>
    <#case "literal_string">
        <#assign literalStringInsn = insn.getAsLiteralStringInsn()>
        <#if func.checkVariableRef(literalStringInsn.resultId())>
            godot_string_new_with_utf8_chars($${literalStringInsn.resultId()}, u8"${literalStringInsn.value()}");
        <#else>
            $${literalStringInsn.resultId()} = godot_new_String_with_utf8_chars(u8"${literalStringInsn.value()}");
        </#if>
        <#break>
    <#case "literal_bool">
        <#assign literalBoolInsn = insn.getAsLiteralBoolInsn()>
        $${literalBoolInsn.resultId()} = ${literalBoolInsn.value?c};
        <#break>
    <#case "literal_int">
        <#assign literalIntInsn = insn.getAsLiteralIntInsn()>
        $${literalIntInsn.resultId()} = ${literalIntInsn.value()};
        <#break>
    <#case "literal_float">
        <#assign literalFloatInsn = insn.getAsLiteralFloatInsn()>
        $${literalFloatInsn.resultId()} = ${literalFloatInsn.value()};
        <#break>
    <#case "literal_null">
        $${insn.resultId()} = NULL;
        <#break>
    <#case "literal_nil">
        <#if func.checkVariableRef(insn.resultId())>
            godot_variant_new_nil($${insn.resultId()});
        <#else>
            $${insn.resultId()} = godot_new_Variant_nil();
        </#if>
        <#break>
</#switch>