package gd.script.gdcc.backend.c.gen;

import gd.script.gdcc.backend.CodegenContext;
import gd.script.gdcc.backend.c.build.COptimizationLevel;
import gd.script.gdcc.backend.c.build.CProjectBuilder;
import gd.script.gdcc.backend.c.build.CProjectInfo;
import gd.script.gdcc.backend.c.build.GodotGdextensionTestRunner;
import gd.script.gdcc.backend.c.build.TargetPlatform;
import gd.script.gdcc.backend.c.build.ZigUtil;
import gd.script.gdcc.enums.GodotVersion;
import gd.script.gdcc.gdextension.ExtensionApiLoader;
import gd.script.gdcc.lir.LirBasicBlock;
import gd.script.gdcc.lir.LirClassDef;
import gd.script.gdcc.lir.LirFunctionDef;
import gd.script.gdcc.lir.LirInstruction;
import gd.script.gdcc.lir.LirModule;
import gd.script.gdcc.lir.LirParameterDef;
import gd.script.gdcc.lir.insn.CallGlobalInsn;
import gd.script.gdcc.lir.insn.LiteralStringInsn;
import gd.script.gdcc.lir.insn.PackVariantInsn;
import gd.script.gdcc.lir.insn.ReturnInsn;
import gd.script.gdcc.lir.insn.UnpackVariantInsn;
import gd.script.gdcc.scope.ClassRegistry;
import gd.script.gdcc.type.GdArrayType;
import gd.script.gdcc.type.GdBoolType;
import gd.script.gdcc.type.GdDictionaryType;
import gd.script.gdcc.type.GdFloatType;
import gd.script.gdcc.type.GdFloatVectorType;
import gd.script.gdcc.type.GdIntVectorType;
import gd.script.gdcc.type.GdObjectType;
import gd.script.gdcc.type.GdPackedNumericArrayType;
import gd.script.gdcc.type.GdStringType;
import gd.script.gdcc.type.GdType;
import gd.script.gdcc.type.GdVariantType;
import gd.script.gdcc.type.GdVoidType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallGlobalInsnGenEngineTest {

    @Test
    @DisplayName("CALL_GLOBAL should call tan/fposmod/lerp/max/print in real engine")
    void callGlobalUtilitiesShouldRunInRealGodot() throws IOException, InterruptedException {
        if (!hasZig()) {
            Assumptions.abort("Zig not found; skipping integration test");
            return;
        }

        var tempDir = Path.of("tmp/test/call_global_engine");
        Files.createDirectories(tempDir);

        var projectInfo = new CProjectInfo(
                "call_global_engine",
                GodotVersion.V451,
                tempDir,
                COptimizationLevel.DEBUG,
                TargetPlatform.getNativePlatform()
        );
        var builder = new CProjectBuilder();
        builder.initProject(projectInfo);

        var callGlobalClass = newCallGlobalEngineClass();
        var module = new LirModule("call_global_engine_module", List.of(callGlobalClass));
        var api = ExtensionApiLoader.loadVersion(GodotVersion.V451);
        var codegen = new CCodegen();
        codegen.prepare(new CodegenContext(projectInfo, new ClassRegistry(api)), module);

        var buildResult = builder.buildProject(projectInfo, codegen);
        assertTrue(buildResult.success(), "Compilation should succeed. Build log:\n" + buildResult.buildLog());
        assertFalse(buildResult.artifacts().isEmpty(), "Compilation should produce extension artifacts.");

        var runner = new GodotGdextensionTestRunner(Path.of("test_project"));
        runner.prepareProject(new GodotGdextensionTestRunner.ProjectSetup(
                buildResult.artifacts(),
                List.of(new GodotGdextensionTestRunner.SceneNodeSpec(
                        "CallGlobalNode",
                        callGlobalClass.getName(),
                        ".",
                        Map.of()
                )),
                new GodotGdextensionTestRunner.TestScriptSpec(testScript())
        ));

        var runResult = runner.run(true);
        var combinedOutput = runResult.combinedOutput();

        assertTrue(runResult.stopSignalSeen(), "Godot run should emit stop signal.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("tan check passed."), "tan should pass.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("fposmod check passed."), "fposmod should pass.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("lerp check passed."), "lerp should pass.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("max check passed."), "max should pass.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("[engine] call_print from extension"), "print should be emitted by extension call.\nOutput:\n" + combinedOutput);
        assertFalse(combinedOutput.contains("check failed"), "No check should fail.\nOutput:\n" + combinedOutput);
    }

    @Test
    @DisplayName("CALL_GLOBAL should execute Variant writeback helper family matrix in real engine")
    void callGlobalVariantWritebackHelperShouldMatchRuntimeFamilyMatrix() throws IOException, InterruptedException {
        if (!hasZig()) {
            Assumptions.abort("Zig not found; skipping integration test");
            return;
        }

        var tempDir = Path.of("tmp/test/call_global_variant_writeback_helper_engine");
        Files.createDirectories(tempDir);

        var projectInfo = new CProjectInfo(
                "call_global_variant_writeback_helper_engine",
                GodotVersion.V451,
                tempDir,
                COptimizationLevel.DEBUG,
                TargetPlatform.getNativePlatform()
        );
        var builder = new CProjectBuilder();
        builder.initProject(projectInfo);

        var probeClass = newVariantWritebackHelperProbeClass();
        var module = new LirModule("call_global_variant_writeback_helper_module", List.of(probeClass));
        var api = ExtensionApiLoader.loadVersion(GodotVersion.V451);
        var codegen = new CCodegen();
        codegen.prepare(new CodegenContext(projectInfo, new ClassRegistry(api)), module);

        var buildResult = builder.buildProject(projectInfo, codegen);
        assertTrue(buildResult.success(), "Compilation should succeed. Build log:\n" + buildResult.buildLog());
        assertFalse(buildResult.artifacts().isEmpty(), "Compilation should produce extension artifacts.");

        var runner = new GodotGdextensionTestRunner(Path.of("test_project"));
        runner.prepareProject(new GodotGdextensionTestRunner.ProjectSetup(
                buildResult.artifacts(),
                List.of(new GodotGdextensionTestRunner.SceneNodeSpec(
                        "CallGlobalWritebackHelperNode",
                        probeClass.getName(),
                        ".",
                        Map.of()
                )),
                new GodotGdextensionTestRunner.TestScriptSpec(variantWritebackHelperTestScript())
        ));

        var runResult = runner.run(true);
        var combinedOutput = runResult.combinedOutput();

        assertTrue(runResult.stopSignalSeen(), "Godot run should emit stop signal.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("helper string true check passed."), "String should require writeback.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("helper vector2 true check passed."), "Vector2 should require writeback.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("helper vector3i true check passed."), "Vector3i should require writeback.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("helper packed array true check passed."), "PackedInt32Array should require writeback.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("helper array false check passed."), "Array should skip writeback.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("helper dictionary false check passed."), "Dictionary should skip writeback.\nOutput:\n" + combinedOutput);
        assertTrue(combinedOutput.contains("helper object false check passed."), "Object should skip writeback.\nOutput:\n" + combinedOutput);
        assertFalse(combinedOutput.contains("check failed"), "No helper-matrix check should fail.\nOutput:\n" + combinedOutput);
    }

    private static boolean hasZig() {
        return ZigUtil.findZig() != null;
    }

    private static LirClassDef newCallGlobalEngineClass() {
        var clazz = new LirClassDef("GDCallGlobalEngineNode", "Node");
        clazz.setSourceFile("call_global_engine.gd");

        var selfType = new GdObjectType(clazz.getName());
        clazz.addFunction(newTanFunction(selfType));
        clazz.addFunction(newFposmodFunction(selfType));
        clazz.addFunction(newLerpFunction(selfType));
        clazz.addFunction(newMaxFunction(selfType));
        clazz.addFunction(newPrintFunction(selfType));
        return clazz;
    }

    private static LirClassDef newVariantWritebackHelperProbeClass() {
        var clazz = new LirClassDef("GDCallGlobalWritebackHelperNode", "Node");
        clazz.setSourceFile("call_global_variant_writeback_helper.gd");

        var selfType = new GdObjectType(clazz.getName());
        clazz.addFunction(newVariantWritebackProbeFunction("probe_string", GdStringType.STRING, selfType));
        clazz.addFunction(newVariantWritebackProbeFunction("probe_vector2", GdFloatVectorType.VECTOR2, selfType));
        clazz.addFunction(newVariantWritebackProbeFunction("probe_vector3i", GdIntVectorType.VECTOR3I, selfType));
        clazz.addFunction(newVariantWritebackProbeFunction(
                "probe_packed_int32_array",
                GdPackedNumericArrayType.PACKED_INT32_ARRAY,
                selfType
        ));
        clazz.addFunction(newVariantWritebackProbeFunction(
                "probe_array",
                new GdArrayType(GdVariantType.VARIANT),
                selfType
        ));
        clazz.addFunction(newVariantWritebackProbeFunction(
                "probe_dictionary",
                new GdDictionaryType(GdVariantType.VARIANT, GdVariantType.VARIANT),
                selfType
        ));
        clazz.addFunction(newVariantWritebackProbeFunction(
                "probe_node_object",
                new GdObjectType("Node"),
                selfType
        ));
        return clazz;
    }

    private static LirFunctionDef newTanFunction(GdObjectType selfType) {
        var func = newMethod("call_tan", GdFloatType.FLOAT, selfType);
        func.addParameter(new LirParameterDef("angleRad", GdFloatType.FLOAT, null, func));
        func.createAndAddVariable("result", GdFloatType.FLOAT);

        entry(func).appendInstruction(new CallGlobalInsn(
                "result",
                "tan",
                List.of(varRef("angleRad"))
        ));
        entry(func).appendInstruction(new ReturnInsn("result"));
        return func;
    }

    private static LirFunctionDef newFposmodFunction(GdObjectType selfType) {
        var func = newMethod("call_fposmod", GdFloatType.FLOAT, selfType);
        func.addParameter(new LirParameterDef("x", GdFloatType.FLOAT, null, func));
        func.addParameter(new LirParameterDef("y", GdFloatType.FLOAT, null, func));
        func.createAndAddVariable("result", GdFloatType.FLOAT);

        entry(func).appendInstruction(new CallGlobalInsn(
                "result",
                "fposmod",
                List.of(varRef("x"), varRef("y"))
        ));
        entry(func).appendInstruction(new ReturnInsn("result"));
        return func;
    }

    private static LirFunctionDef newLerpFunction(GdObjectType selfType) {
        var func = newMethod("call_lerp", GdFloatType.FLOAT, selfType);
        func.addParameter(new LirParameterDef("from", GdFloatType.FLOAT, null, func));
        func.addParameter(new LirParameterDef("to", GdFloatType.FLOAT, null, func));
        func.addParameter(new LirParameterDef("weight", GdFloatType.FLOAT, null, func));

        func.createAndAddVariable("fromVariant", GdVariantType.VARIANT);
        func.createAndAddVariable("toVariant", GdVariantType.VARIANT);
        func.createAndAddVariable("weightVariant", GdVariantType.VARIANT);
        func.createAndAddVariable("resultVariant", GdVariantType.VARIANT);
        func.createAndAddVariable("result", GdFloatType.FLOAT);

        entry(func).appendInstruction(new PackVariantInsn("fromVariant", "from"));
        entry(func).appendInstruction(new PackVariantInsn("toVariant", "to"));
        entry(func).appendInstruction(new PackVariantInsn("weightVariant", "weight"));
        entry(func).appendInstruction(new CallGlobalInsn(
                "resultVariant",
                "lerp",
                List.of(varRef("fromVariant"), varRef("toVariant"), varRef("weightVariant"))
        ));
        entry(func).appendInstruction(new UnpackVariantInsn("result", "resultVariant"));
        entry(func).appendInstruction(new ReturnInsn("result"));
        return func;
    }

    private static LirFunctionDef newMaxFunction(GdObjectType selfType) {
        var func = newMethod("call_max", GdFloatType.FLOAT, selfType);
        func.addParameter(new LirParameterDef("a", GdFloatType.FLOAT, null, func));
        func.addParameter(new LirParameterDef("b", GdFloatType.FLOAT, null, func));
        func.addParameter(new LirParameterDef("c", GdFloatType.FLOAT, null, func));

        func.createAndAddVariable("aVariant", GdVariantType.VARIANT);
        func.createAndAddVariable("bVariant", GdVariantType.VARIANT);
        func.createAndAddVariable("cVariant", GdVariantType.VARIANT);
        func.createAndAddVariable("resultVariant", GdVariantType.VARIANT);
        func.createAndAddVariable("result", GdFloatType.FLOAT);

        entry(func).appendInstruction(new PackVariantInsn("aVariant", "a"));
        entry(func).appendInstruction(new PackVariantInsn("bVariant", "b"));
        entry(func).appendInstruction(new PackVariantInsn("cVariant", "c"));
        entry(func).appendInstruction(new CallGlobalInsn(
                "resultVariant",
                "max",
                List.of(varRef("aVariant"), varRef("bVariant"), varRef("cVariant"))
        ));
        entry(func).appendInstruction(new UnpackVariantInsn("result", "resultVariant"));
        entry(func).appendInstruction(new ReturnInsn("result"));
        return func;
    }

    private static LirFunctionDef newPrintFunction(GdObjectType selfType) {
        var func = newMethod("call_print", GdVoidType.VOID, selfType);
        func.createAndAddVariable("messageText", GdStringType.STRING);
        func.createAndAddVariable("messageVariant", GdVariantType.VARIANT);

        entry(func).appendInstruction(new LiteralStringInsn("messageText", "[engine] call_print from extension"));
        entry(func).appendInstruction(new PackVariantInsn("messageVariant", "messageText"));
        entry(func).appendInstruction(new CallGlobalInsn(
                null,
                "print",
                List.of(varRef("messageVariant"))
        ));
        entry(func).appendInstruction(new ReturnInsn(null));
        return func;
    }

    private static LirFunctionDef newVariantWritebackProbeFunction(
            String name,
            GdType probeType,
            GdObjectType selfType
    ) {
        var func = newMethod(name, GdBoolType.BOOL, selfType);
        func.addParameter(new LirParameterDef("value", probeType, null, func));
        func.createAndAddVariable("carrier", GdVariantType.VARIANT);
        func.createAndAddVariable("requiresWriteback", GdBoolType.BOOL);

        entry(func).appendInstruction(new PackVariantInsn("carrier", "value"));
        entry(func).appendInstruction(new CallGlobalInsn(
                "requiresWriteback",
                "gdcc_variant_requires_writeback",
                List.of(varRef("carrier"))
        ));
        entry(func).appendInstruction(new ReturnInsn("requiresWriteback"));
        return func;
    }

    private static LirFunctionDef newMethod(String name, GdType returnType, GdObjectType selfType) {
        var func = new LirFunctionDef(name);
        func.setReturnType(returnType);
        func.addParameter(new LirParameterDef("self", selfType, null, func));
        func.addBasicBlock(new LirBasicBlock("entry"));
        func.setEntryBlockId("entry");
        return func;
    }

    private static LirBasicBlock entry(LirFunctionDef functionDef) {
        return functionDef.getBasicBlock("entry");
    }

    private static LirInstruction.VariableOperand varRef(String id) {
        return new LirInstruction.VariableOperand(id);
    }

    private static String testScript() {
        return """
                extends Node
                
                const TARGET_NODE_NAME = "CallGlobalNode"
                const EPSILON = 0.001
                
                func _ready() -> void:
                    var target = get_parent().get_node_or_null(TARGET_NODE_NAME)
                    if target == null:
                        push_error("Target node missing.")
                        return
                
                    var tan_value = float(target.call("call_tan", 0.5))
                    if absf(tan_value - tan(0.5)) <= EPSILON:
                        print("tan check passed.")
                    else:
                        push_error("tan check failed.")
                
                    var fposmod_value = float(target.call("call_fposmod", -1.5, 1.0))
                    if absf(fposmod_value - fposmod(-1.5, 1.0)) <= EPSILON:
                        print("fposmod check passed.")
                    else:
                        push_error("fposmod check failed.")
                
                    var lerp_value = float(target.call("call_lerp", 10.0, 20.0, 0.25))
                    if absf(lerp_value - lerp(10.0, 20.0, 0.25)) <= EPSILON:
                        print("lerp check passed.")
                    else:
                        push_error("lerp check failed.")
                
                    var max_value = float(target.call("call_max", 1.25, 4.5, 2.75))
                    if absf(max_value - max(1.25, 4.5, 2.75)) <= EPSILON:
                        print("max check passed.")
                    else:
                        push_error("max check failed.")
                
                    target.call("call_print")
                """;
    }

    private static String variantWritebackHelperTestScript() {
        return """
                extends Node
                
                const TARGET_NODE_NAME = "CallGlobalWritebackHelperNode"
                
                func _ready() -> void:
                    var target = get_parent().get_node_or_null(TARGET_NODE_NAME)
                    if target == null:
                        push_error("Target node missing.")
                        return
                
                    if bool(target.call("probe_string", "alpha")):
                        print("helper string true check passed.")
                    else:
                        push_error("helper string true check failed.")
                
                    if bool(target.call("probe_vector2", Vector2(1.0, 2.0))):
                        print("helper vector2 true check passed.")
                    else:
                        push_error("helper vector2 true check failed.")
                
                    if bool(target.call("probe_vector3i", Vector3i(1, 2, 3))):
                        print("helper vector3i true check passed.")
                    else:
                        push_error("helper vector3i true check failed.")
                
                    if bool(target.call("probe_packed_int32_array", PackedInt32Array([1, 2]))):
                        print("helper packed array true check passed.")
                    else:
                        push_error("helper packed array true check failed.")
                
                    if not bool(target.call("probe_array", [1, 2])):
                        print("helper array false check passed.")
                    else:
                        push_error("helper array false check failed.")
                
                    if not bool(target.call("probe_dictionary", {"alpha": 1})):
                        print("helper dictionary false check passed.")
                    else:
                        push_error("helper dictionary false check failed.")
                
                    if not bool(target.call("probe_node_object", Node.new())):
                        print("helper object false check passed.")
                    else:
                        push_error("helper object false check failed.")
                """;
    }
}
