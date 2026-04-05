package dev.superice.gdcc.backend.c.build;

import dev.superice.gdcc.backend.CodegenContext;
import dev.superice.gdcc.backend.c.gen.CCodegen;
import dev.superice.gdcc.enums.GodotVersion;
import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.lowering.FrontendLoweringPassManager;
import dev.superice.gdcc.frontend.parse.FrontendModule;
import dev.superice.gdcc.frontend.parse.GdScriptParserService;
import dev.superice.gdcc.gdextension.ExtensionApiLoader;
import dev.superice.gdcc.scope.ClassRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FrontendLoweringToCProjectBuilderIntegrationTest {
    @Test
    void lowerFrontendModuleBuildNativeLibraryAndRunInGodot() throws Exception {
        if (ZigUtil.findZig() == null) {
            Assumptions.abort("Zig not found; skipping frontend-to-native Godot integration test");
            return;
        }

        var tempDir = Path.of("tmp/test/frontend_lowering_build_smoke");
        Files.createDirectories(tempDir);

        var source = """
                class_name FrontendBuildSmoke
                extends Node
                
                func ping() -> int:
                    var obj: Object = null;
                    return 1
                """;
        var module = parseModule(
                tempDir.resolve("frontend_build_smoke.gd"),
                source,
                Map.of("FrontendBuildSmoke", "RuntimeFrontendBuildSmoke")
        );
        var diagnostics = new DiagnosticManager();
        var classRegistry = new ClassRegistry(ExtensionApiLoader.loadVersion(GodotVersion.V451));
        var lowered = new FrontendLoweringPassManager().lower(module, classRegistry, diagnostics);

        assertNotNull(lowered);
        assertFalse(diagnostics.hasErrors(), () -> "Unexpected frontend diagnostics: " + diagnostics.snapshot());
        assertEquals(1, lowered.getClassDefs().size());
        assertEquals("RuntimeFrontendBuildSmoke", lowered.getClassDefs().getFirst().getName());
        assertEquals(1, lowered.getClassDefs().getFirst().getFunctions().size());
        assertEquals("ping", lowered.getClassDefs().getFirst().getFunctions().getFirst().getName());
        assertFalse(lowered.getClassDefs().getFirst().getFunctions().getFirst().getEntryBlockId().isBlank());

        var projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        var projectInfo = new CProjectInfo(
                "frontend_build_smoke",
                GodotVersion.V451,
                projectDir,
                COptimizationLevel.DEBUG,
                TargetPlatform.getNativePlatform()
        );
        var codegen = new CCodegen();
        codegen.prepare(new CodegenContext(projectInfo, classRegistry), lowered);

        var buildResult = new CProjectBuilder().buildProject(projectInfo, codegen);
        var librarySuffix = projectInfo.getTargetPlatform().sharedLibraryFileName("artifact").replace("artifact", "");

        assertTrue(buildResult.success(), () -> "Native build should succeed. Build log:\n" + buildResult.buildLog());
        assertTrue(Files.exists(projectDir.resolve("entry.c")));
        assertTrue(Files.exists(projectDir.resolve("entry.h")));
        assertTrue(
                buildResult.artifacts().stream()
                        .anyMatch(artifact -> artifact.getFileName().toString().endsWith(librarySuffix)),
                () -> "Expected a native library artifact with suffix '" + librarySuffix + "', got " + buildResult.artifacts()
        );
        assertTrue(buildResult.artifacts().stream().allMatch(Files::exists));

        var runner = new GodotGdextensionTestRunner(Path.of("test_project"));
        runner.prepareProject(new GodotGdextensionTestRunner.ProjectSetup(
                buildResult.artifacts(),
                List.of(new GodotGdextensionTestRunner.SceneNodeSpec(
                        "FrontendBuildSmokeNode",
                        lowered.getClassDefs().getFirst().getName(),
                        ".",
                        Map.of()
                )),
                new GodotGdextensionTestRunner.TestScriptSpec(testScript())
        ));

        var runResult = runner.run(true);
        var combinedOutput = runResult.combinedOutput();

        assertTrue(
                runResult.stopSignalSeen(),
                () -> "Godot run should emit \"" + GodotGdextensionTestRunner.TEST_STOP_SIGNAL + "\".\nOutput:\n" + combinedOutput
        );
        assertTrue(
                combinedOutput.contains("frontend lowering runtime ping check passed."),
                () -> "Godot output should confirm ping result.\nOutput:\n" + combinedOutput
        );
        assertFalse(
                combinedOutput.contains("frontend lowering runtime ping check failed."),
                () -> "Ping check should not fail.\nOutput:\n" + combinedOutput
        );
    }

    @Test
    void lowerFrontendConstructorRoutesBuildNativeLibraryAndRunInGodot() throws Exception {
        if (ZigUtil.findZig() == null) {
            Assumptions.abort("Zig not found; skipping frontend constructor integration test");
            return;
        }

        var tempDir = Path.of("tmp/test/frontend_constructor_runtime");
        Files.createDirectories(tempDir);

        var constructorSource = """
                class_name ConstructorSmoke
                extends Node
                
                func make_vector() -> Vector3i:
                    return Vector3i(1, 2, 3)
                
                func make_node_class_name() -> String:
                    return Node.new().get_class()
                
                func make_ref_counted() -> RefCounted:
                    return RefCounted.new()
                
                func measure_ref_counted_reference_count() -> int:
                    return RefCounted.new().get_reference_count()
                
                func make_worker_value() -> int:
                    return Worker.new().read()
                
                func measure_worker_reference_count() -> int:
                    return Worker.new().get_reference_count()
                
                func make_plain_worker_value() -> int:
                    return PlainWorker.new().id()
                """;
        var workerSource = """
                class_name Worker
                extends RefCounted
                
                var seed: int
                
                func _init() -> void:
                    seed = 7
                
                func read() -> int:
                    return seed
                """;
        var plainWorkerSource = """
                class_name PlainWorker
                extends Object
                
                func id() -> int:
                    return 9
                """;
        var module = parseModule(
                "frontend_constructor_runtime_module",
                List.of(
                        new SourceFileSpec(tempDir.resolve("constructor_smoke.gd"), constructorSource),
                        new SourceFileSpec(tempDir.resolve("worker.gd"), workerSource),
                        new SourceFileSpec(tempDir.resolve("plain_worker.gd"), plainWorkerSource)
                ),
                Map.of(
                        "ConstructorSmoke", "RuntimeConstructorSmoke",
                        "Worker", "RuntimeConstructorWorker",
                        "PlainWorker", "RuntimePlainWorker"
                )
        );
        var diagnostics = new DiagnosticManager();
        var classRegistry = new ClassRegistry(ExtensionApiLoader.loadVersion(GodotVersion.V451));
        var lowered = new FrontendLoweringPassManager().lower(module, classRegistry, diagnostics);

        assertNotNull(lowered);
        assertFalse(diagnostics.hasErrors(), () -> "Unexpected frontend diagnostics: " + diagnostics.snapshot());
        assertEquals(3, lowered.getClassDefs().size());
        assertTrue(
                lowered.getClassDefs().stream().anyMatch(classDef -> "RuntimeConstructorSmoke".equals(classDef.getName()))
        );
        assertTrue(
                lowered.getClassDefs().stream().anyMatch(classDef -> "RuntimeConstructorWorker".equals(classDef.getName()))
        );
        assertTrue(
                lowered.getClassDefs().stream().anyMatch(classDef -> "RuntimePlainWorker".equals(classDef.getName()))
        );

        var projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        var projectInfo = new CProjectInfo(
                "frontend_constructor_runtime",
                GodotVersion.V451,
                projectDir,
                COptimizationLevel.DEBUG,
                TargetPlatform.getNativePlatform()
        );
        var codegen = new CCodegen();
        codegen.prepare(new CodegenContext(projectInfo, classRegistry), lowered);

        var buildResult = new CProjectBuilder().buildProject(projectInfo, codegen);
        var entrySource = Files.readString(projectDir.resolve("entry.c"));

        assertTrue(buildResult.success(), () -> "Native build should succeed. Build log:\n" + buildResult.buildLog());
        assertTrue(entrySource.contains("godot_new_Vector3i_with_int_int_int"), entrySource);
        assertTrue(entrySource.contains("godot_new_Node()"), entrySource);
        assertTrue(entrySource.contains("godot_new_RefCounted()"), entrySource);
        assertTrue(
                entrySource.contains("gdcc_ref_counted_init_raw(RuntimeConstructorWorker_class_create_instance(NULL, false), true)"),
                entrySource
        );
        assertTrue(entrySource.contains("RuntimePlainWorker_class_create_instance(NULL, true)"), entrySource);
        assertTrue(entrySource.contains("RuntimeConstructorWorker__init(self);"), entrySource);
        assertFalse(
                entrySource.contains("gdcc_ref_counted_init_raw(godot_classdb_construct_object2(GD_STATIC_SN(u8\"RefCounted\")))"),
                entrySource
        );

        var runner = new GodotGdextensionTestRunner(Path.of("test_project"));
        runner.prepareProject(new GodotGdextensionTestRunner.ProjectSetup(
                buildResult.artifacts(),
                List.of(new GodotGdextensionTestRunner.SceneNodeSpec(
                        "FrontendConstructorNode",
                        "RuntimeConstructorSmoke",
                        ".",
                        Map.of()
                )),
                new GodotGdextensionTestRunner.TestScriptSpec(constructorTestScript())
        ));

        var runResult = runner.run(true);
        var combinedOutput = runResult.combinedOutput();

        assertTrue(
                runResult.stopSignalSeen(),
                () -> "Godot run should emit \"" + GodotGdextensionTestRunner.TEST_STOP_SIGNAL + "\".\nOutput:\n" + combinedOutput
        );
        assertTrue(
                combinedOutput.contains("frontend constructor builtin check passed."),
                () -> "Builtin constructor check should pass.\nOutput:\n" + combinedOutput
        );
        assertTrue(
                combinedOutput.contains("frontend constructor engine object check passed."),
                () -> "Engine constructor check should pass.\nOutput:\n" + combinedOutput
        );
        assertTrue(
                combinedOutput.contains("frontend constructor engine refcounted lifecycle check passed."),
                () -> "Engine RefCounted runtime check should pass.\nOutput:\n" + combinedOutput
        );
        assertTrue(
                combinedOutput.contains("frontend constructor gdcc refcounted lifecycle check passed."),
                () -> "GDCC RefCounted runtime check should pass.\nOutput:\n" + combinedOutput
        );
        assertTrue(
                combinedOutput.contains("frontend constructor gdcc plain object lifecycle check passed."),
                () -> "GDCC plain object runtime check should pass.\nOutput:\n" + combinedOutput
        );
        assertFalse(
                combinedOutput.contains("check failed."),
                () -> "Constructor integration output should not include failure markers.\nOutput:\n" + combinedOutput
        );
    }

    private static @NotNull FrontendModule parseModule(
            @NotNull Path sourcePath,
            @NotNull String source,
            @NotNull Map<String, String> topLevelCanonicalNameMap
    ) {
        return parseModule(
                "frontend_build_smoke_module",
                List.of(new SourceFileSpec(sourcePath, source)),
                topLevelCanonicalNameMap
        );
    }

    private static @NotNull FrontendModule parseModule(
            @NotNull String moduleName,
            @NotNull List<SourceFileSpec> sources,
            @NotNull Map<String, String> topLevelCanonicalNameMap
    ) {
        var parser = new GdScriptParserService();
        var parseDiagnostics = new DiagnosticManager();
        var units = sources.stream()
                .map(sourceFile -> parser.parseUnit(sourceFile.sourcePath(), sourceFile.source(), parseDiagnostics))
                .toList();
        assertTrue(parseDiagnostics.isEmpty(), () -> "Unexpected parse diagnostics: " + parseDiagnostics.snapshot());
        return new FrontendModule(moduleName, units, topLevelCanonicalNameMap);
    }

    private static @NotNull String testScript() {
        return """
                extends Node
                
                const TARGET_NODE_NAME = "FrontendBuildSmokeNode"
                
                func _ready() -> void:
                    var target = get_parent().get_node_or_null(TARGET_NODE_NAME)
                    if target == null:
                        push_error("Target node missing.")
                        return
                
                    var result = int(target.call("ping"))
                    if result == 1:
                        print("frontend lowering runtime ping check passed.")
                    else:
                        push_error("frontend lowering runtime ping check failed.")
                """;
    }

    private static @NotNull String constructorTestScript() {
        return """
                extends Node
                
                const TARGET_NODE_NAME = "FrontendConstructorNode"
                
                func _ready() -> void:
                    var target = get_parent().get_node_or_null(TARGET_NODE_NAME)
                    if target == null:
                        push_error("Target node missing.")
                        return
                
                    var vector = target.call("make_vector")
                    if typeof(vector) == TYPE_VECTOR3I and vector == Vector3i(1, 2, 3):
                        print("frontend constructor builtin check passed.")
                    else:
                        push_error("frontend constructor builtin check failed.")
                
                    var node_class = String(target.call("make_node_class_name"))
                    if node_class == "Node":
                        print("frontend constructor engine object check passed.")
                    else:
                        push_error("frontend constructor engine object check failed.")
                
                    var engine_ref_count = int(target.call("measure_ref_counted_reference_count"))
                    if engine_ref_count >= 1:
                        print("frontend constructor engine refcounted lifecycle check passed.")
                    else:
                        push_error("frontend constructor engine refcounted lifecycle check failed.")
                
                    var worker_value = int(target.call("make_worker_value"))
                    var worker_ref_count = int(target.call("measure_worker_reference_count"))
                    if worker_value == 7 and worker_ref_count >= 1:
                        print("frontend constructor gdcc refcounted lifecycle check passed.")
                    else:
                        push_error("frontend constructor gdcc refcounted lifecycle check failed.")
                
                    var plain_worker_value = int(target.call("make_plain_worker_value"))
                    if plain_worker_value == 9:
                        print("frontend constructor gdcc plain object lifecycle check passed.")
                    else:
                        push_error("frontend constructor gdcc plain object lifecycle check failed.")
                """;
    }

    private record SourceFileSpec(@NotNull Path sourcePath, @NotNull String source) {
    }
}
