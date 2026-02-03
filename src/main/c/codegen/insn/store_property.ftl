<#-- @ftlvariable name="insn" type="dev.superice.gdcc.lir.insn.StorePropertyInsn" -->
<#-- @ftlvariable name="func" type="dev.superice.gdcc.lir.LirFunctionDef" -->
<#-- @ftlvariable name="helper" type="dev.superice.gdcc.backend.c.gen.CGenHelper" -->
<#-- @ftlvariable name="gen" type="dev.superice.gdcc.backend.c.gen.insn.StorePropertyInsnGen" -->
<#-- @ftlvariable name="genMode" type="java.lang.String" -->
<#-- @ftlvariable name="insideSelfSetter" type="java.lang.Boolean" -->
<#-- @ftlvariable name="gdccSetterName" type="java.lang.String" -->
<#assign valueType = func.getVariableById(insn.valueId).type>
<#assign objectType = func.getVariableById(insn.objectId).type>

<#switch genMode>
    <#case "gdcc">
    <#-- If we are inside the setter for this field itself, assign the backing field directly to avoid recursion -->
        <#if insideSelfSetter>
            $${insn.objectId}->${insn.propertyName} = ${helper.renderCopyAssignFunctionName(valueType)}(${helper.renderVarRef(func, insn.valueId)});
        <#else>
            ${valueType.typeName}_${gdccSetterName}($${insn.objectId}, ${helper.renderVarRef(func, insn.valueId)});
        </#if>
        <#break>
    <#case "engine">
        godot_${objectType.typeName}_set_${insn.propertyName}($${insn.objectId}, ${helper.renderVarRef(func, insn.valueId)});
        <#break>
    <#case "general">
        {
            godot_Variant __temp_variant = ${helper.renderPackFunctionName(valueType)}(${helper.renderVarRef(func, insn.valueId)});
        <#if helper.checkGdccType(objectType)>
            godot_Object_set($${insn.objectId}->_object, GD_STATIC_SN(u8"${insn.propertyName}"), &__temp_variant);
        <#else>
            godot_Object_set($${insn.objectId}, GD_STATIC_SN(u8"${insn.propertyName}"), &__temp_variant);
        </#if>
        }
        <#break>
    <#case "builtin">
        <#if func.checkVariableRef(insn.objectId)>
            godot_${valueType.typeName}_set_${insn.propertyName}($${insn.objectId}, ${helper.renderVarRef(func, insn.valueId)});
        <#else>
            godot_${valueType.typeName}_set_${insn.propertyName}(&$${insn.objectId}, ${helper.renderVarRef(func, insn.valueId)});
        </#if>
        <#break>
</#switch>
