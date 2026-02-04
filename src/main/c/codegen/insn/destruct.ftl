<#-- @ftlvariable name="insn" type="dev.superice.gdcc.lir.insn.DestructInsn" -->
<#-- @ftlvariable name="func" type="dev.superice.gdcc.lir.LirFunctionDef" -->
<#-- @ftlvariable name="helper" type="dev.superice.gdcc.backend.c.gen.CGenHelper" -->
<#-- @ftlvariable name="gen" type="dev.superice.gdcc.backend.c.gen.insn.DestructInsnGen" -->
<#-- @ftlvariable name="genMode" type="java.lang.String" -->
<#-- @ftlvariable name="typeName" type="java.lang.String" -->
<#switch gen>
    <#case "ref_counted_gdcc">
        own_object($${insn.variableId}->_object);
    <#break>
    <#case "ref_counted">
        own_object($${insn.variableId});
    <#break>
    <#case "engine_object">
        godot_object_destroy($${insn.variableId});
    <#break>
    <#case "gdcc_object">
        godot_object_destroy($${insn.variableId}->_object);
    <#break>
    <#case "general_object">
        try_destroy_object($${insn.variableId});
    <#break>
    <#case "variant">
        <#if func.checkVariableRef(insn.variableId)>
            godot_variant_destroy($${insn.variableId});
        <#else>
            godot_variant_destroy(&$${insn.variableId});
        </#if>
    <#break>
    <#case "str_meta_container">
        <#if func.checkVariableRef(insn.variableId)>
            godot_${typeName}_destroy($${insn.variableId});
        <#else>
            godot_${typeName}_destroy(&$${insn.variableId});
        </#if>
    <#break>
</#switch>