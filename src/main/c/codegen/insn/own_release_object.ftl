<#-- @ftlvariable name="insn" type="dev.superice.gdcc.lir.insn.TryOwnObjectInsn" -->
<#-- @ftlvariable name="func" type="dev.superice.gdcc.lir.LirFunctionDef" -->
<#-- @ftlvariable name="helper" type="dev.superice.gdcc.backend.c.gen.CGenHelper" -->
<#-- @ftlvariable name="assertRefCounted" type="java.lang.Boolean" -->
<#-- @ftlvariable name="gdcc" type="java.lang.Boolean" -->
<#if insn.opcode().opcode() == "try_own_object">
    <#if assertRefCounted>
        <#if gdcc>
            own_object($${insn.objectId}->_object);
        <#else>
            own_object($${insn.objectId});
        </#if>
    <#else>
        <#if gdcc>
            try_own_object($${insn.objectId}->_object);
        <#else>
            try_own_object($${insn.objectId});
        </#if>
    </#if>
<#else>
    <#if assertRefCounted>
        <#if gdcc>
            release_object($${insn.objectId}->_object);
        <#else>
            release_object($${insn.objectId});
        </#if>
    <#else>
        <#if gdcc>
            try_release_object($${insn.objectId}->_object);
        <#else>
            try_release_object($${insn.objectId});
        </#if>
    </#if>
</#if>