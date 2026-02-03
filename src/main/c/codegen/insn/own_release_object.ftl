<#-- @ftlvariable name="insn" type="dev.superice.gdcc.lir.insn.TryOwnObjectInsn" -->
<#-- @ftlvariable name="func" type="dev.superice.gdcc.lir.LirFunctionDef" -->
<#-- @ftlvariable name="helper" type="dev.superice.gdcc.backend.c.gen.CGenHelper" -->
<#-- @ftlvariable name="assertRefCounted" type="java.lang.Boolean" -->
<#if insn.opcode().opcode() == "try_own_object">
    <#if assertRefCounted>
        own_object($${insn.objectId});
    <#else>
        try_own_object($${insn.objectId});
    </#if>
<#else>
    <#if assertRefCounted>
        release_object($${insn.objectId});
    <#else>
        try_release_object($${insn.objectId});
    </#if>
</#if>