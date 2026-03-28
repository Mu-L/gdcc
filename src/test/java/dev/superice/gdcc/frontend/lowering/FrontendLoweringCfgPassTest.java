package dev.superice.gdcc.frontend.lowering;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.lowering.pass.FrontendLoweringAnalysisPass;
import dev.superice.gdcc.frontend.lowering.pass.FrontendLoweringCfgPass;
import dev.superice.gdcc.frontend.lowering.pass.FrontendLoweringClassSkeletonPass;
import dev.superice.gdcc.frontend.lowering.pass.FrontendLoweringFunctionPreparationPass;
import dev.superice.gdcc.frontend.parse.FrontendModule;
import dev.superice.gdcc.frontend.parse.GdScriptParserService;
import dev.superice.gdcc.frontend.sema.FrontendAnalysisData;
import dev.superice.gdcc.gdextension.ExtensionApiLoader;
import dev.superice.gdcc.lir.LirModule;
import dev.superice.gdcc.scope.ClassRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendLoweringCfgPassTest {
    @Test
    void runValidatesExecutableContextsAndLeavesPropertyInitShellOnly() throws Exception {
        var prepared = prepareCompileReadyContext();

        new FrontendLoweringCfgPass().run(prepared.context());

        var contexts = prepared.context().requireFunctionLoweringContexts();
        var executableContext = requireContext(
                contexts,
                FunctionLoweringContext.Kind.EXECUTABLE_BODY,
                "RuntimeCfgPassReady",
                "ping"
        );
        var propertyContext = requireContext(
                contexts,
                FunctionLoweringContext.Kind.PROPERTY_INIT,
                "RuntimeCfgPassReady",
                "_field_init_ready_value"
        );

        assertAll(
                () -> assertSame(prepared.context().requireAnalysisData(), executableContext.analysisData()),
                () -> assertEquals(0, executableContext.targetFunction().getBasicBlockCount()),
                () -> assertTrue(executableContext.targetFunction().getEntryBlockId().isEmpty()),
                () -> assertEquals(0, propertyContext.targetFunction().getBasicBlockCount()),
                () -> assertTrue(propertyContext.targetFunction().getEntryBlockId().isEmpty()),
                () -> assertFalse(prepared.diagnostics().hasErrors())
        );
    }

    @Test
    void runFailsFastBeforeAnalysisPublication() throws Exception {
        var context = new FrontendLoweringContext(
                new FrontendModule("test_module", List.of()),
                new ClassRegistry(ExtensionApiLoader.loadDefault()),
                new DiagnosticManager()
        );

        var exception = assertThrows(IllegalStateException.class, () -> new FrontendLoweringCfgPass().run(context));

        assertTrue(exception.getMessage().contains("analysisData"), exception.getMessage());
    }

    @Test
    void runFailsFastBeforeLirModulePublication() throws Exception {
        var context = new FrontendLoweringContext(
                new FrontendModule("test_module", List.of()),
                new ClassRegistry(ExtensionApiLoader.loadDefault()),
                new DiagnosticManager()
        );
        context.publishAnalysisData(FrontendAnalysisData.bootstrap());

        var exception = assertThrows(IllegalStateException.class, () -> new FrontendLoweringCfgPass().run(context));

        assertTrue(exception.getMessage().contains("lirModule"), exception.getMessage());
    }

    @Test
    void runFailsFastBeforeFunctionLoweringContextsPublication() throws Exception {
        var context = new FrontendLoweringContext(
                new FrontendModule("test_module", List.of()),
                new ClassRegistry(ExtensionApiLoader.loadDefault()),
                new DiagnosticManager()
        );
        context.publishAnalysisData(FrontendAnalysisData.bootstrap());
        context.publishLirModule(new LirModule("test_module", List.of()));

        var exception = assertThrows(IllegalStateException.class, () -> new FrontendLoweringCfgPass().run(context));

        assertTrue(exception.getMessage().contains("functionLoweringContexts"), exception.getMessage());
    }

    @Test
    void runRejectsParameterDefaultContextsUntilTheirCompileSurfaceExists() throws Exception {
        var prepared = prepareCompileReadyContext();
        var executableContext = requireContext(
                prepared.context().requireFunctionLoweringContexts(),
                FunctionLoweringContext.Kind.EXECUTABLE_BODY,
                "RuntimeCfgPassReady",
                "ping"
        );
        var parameterDefaultContext = new FunctionLoweringContext(
                FunctionLoweringContext.Kind.PARAMETER_DEFAULT_INIT,
                executableContext.sourcePath(),
                executableContext.sourceClassRelation(),
                executableContext.owningClass(),
                executableContext.targetFunction(),
                executableContext.sourceOwner(),
                executableContext.loweringRoot(),
                executableContext.analysisData()
        );
        prepared.context().publishFunctionLoweringContexts(List.of(executableContext, parameterDefaultContext));

        var exception = assertThrows(
                IllegalStateException.class,
                () -> new FrontendLoweringCfgPass().run(prepared.context())
        );

        assertTrue(exception.getMessage().contains("parameter default"), exception.getMessage());
    }

    @Test
    void compileBlockedModuleStopsBeforeCfgPassRuns() throws Exception {
        var cfgRan = new AtomicBoolean();
        var continuationRan = new AtomicBoolean();
        var diagnostics = new DiagnosticManager();
        var lowered = new FrontendLoweringPassManager(List.of(
                new FrontendLoweringAnalysisPass(),
                new FrontendLoweringClassSkeletonPass(),
                new FrontendLoweringFunctionPreparationPass(),
                context -> {
                    cfgRan.set(true);
                    new FrontendLoweringCfgPass().run(context);
                },
                _ -> continuationRan.set(true)
        )).lower(
                parseModule(
                        List.of(new SourceFixture(
                                "cfg_pass_compile_blocked.gd",
                                """
                                        class_name CfgPassCompileBlocked
                                        extends RefCounted
                                        
                                        func ping(value):
                                            assert(value, "blocked in compile mode")
                                        """
                        )),
                        Map.of()
                ),
                new ClassRegistry(ExtensionApiLoader.loadDefault()),
                diagnostics
        );

        assertAll(
                () -> assertNull(lowered),
                () -> assertFalse(cfgRan.get()),
                () -> assertFalse(continuationRan.get()),
                () -> assertTrue(diagnostics.hasErrors())
        );
    }

    private static @NotNull PreparedContext prepareCompileReadyContext() throws Exception {
        var diagnostics = new DiagnosticManager();
        var module = parseModule(
                List.of(new SourceFixture(
                        "cfg_pass_ready.gd",
                        """
                                class_name CfgPassReady
                                extends RefCounted
                                
                                var ready_value: int = 1
                                
                                func ping(value: int) -> int:
                                    return value + 1
                                """
                )),
                Map.of("CfgPassReady", "RuntimeCfgPassReady")
        );
        var context = new FrontendLoweringContext(
                module,
                new ClassRegistry(ExtensionApiLoader.loadDefault()),
                diagnostics
        );
        new FrontendLoweringAnalysisPass().run(context);
        new FrontendLoweringClassSkeletonPass().run(context);
        new FrontendLoweringFunctionPreparationPass().run(context);
        return new PreparedContext(context, diagnostics);
    }

    private static @NotNull FrontendModule parseModule(
            @NotNull List<SourceFixture> fixtures,
            @NotNull Map<String, String> topLevelCanonicalNameMap
    ) {
        var parserService = new GdScriptParserService();
        var parseDiagnostics = new DiagnosticManager();
        var units = fixtures.stream()
                .map(fixture -> parserService.parseUnit(Path.of("tmp", fixture.fileName()), fixture.source(), parseDiagnostics))
                .toList();
        assertTrue(parseDiagnostics.isEmpty(), () -> "Unexpected parse diagnostics: " + parseDiagnostics.snapshot());
        return new FrontendModule("test_module", units, topLevelCanonicalNameMap);
    }

    private static @NotNull FunctionLoweringContext requireContext(
            @NotNull List<FunctionLoweringContext> contexts,
            @NotNull FunctionLoweringContext.Kind kind,
            @NotNull String owningClassName,
            @NotNull String functionName
    ) {
        return contexts.stream()
                .filter(context -> context.kind() == kind)
                .filter(context -> context.owningClass().getName().equals(owningClassName))
                .filter(context -> context.targetFunction().getName().equals(functionName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing context " + kind + " " + owningClassName + "." + functionName
                ));
    }

    private record PreparedContext(
            @NotNull FrontendLoweringContext context,
            @NotNull DiagnosticManager diagnostics
    ) {
    }

    private record SourceFixture(
            @NotNull String fileName,
            @NotNull String source
    ) {
    }
}
