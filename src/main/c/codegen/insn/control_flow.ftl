<#-- @ftlvariable name="insn" type="dev.superice.gdcc.lir.insn.ControlFlowInstruction" -->
<#-- @ftlvariable name="func" type="dev.superice.gdcc.lir.LirFunctionDef" -->
<#-- @ftlvariable name="helper" type="dev.superice.gdcc.backend.c.gen.CGenHelper" -->
<#switch insn.opcode().opcode()>
    <#case "goto">
        <#assign gotoInsn = insn.asGotoInsn>
        goto ${gotoInsn.targetBbId};
        <#break>
    <#case "go_if">
        <#assign goIfInsn = insn.asGoIfInsn>
        if ($${goIfInsn.conditionVarId}) {
            goto ${goIfInsn.trueBbId};
        } else {
            goto ${goIfInsn.falseBbId};
        }
        <#break>
    <#case "return">
        <#assign returnInsn = insn.asReturnInsn>
        <#if returnInsn.returnValueId??>
            return $${returnInsn.returnValueId};
        <#else>
            return;
        </#if>
        <#break>
</#switch>