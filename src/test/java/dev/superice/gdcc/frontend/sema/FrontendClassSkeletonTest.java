package dev.superice.gdcc.frontend.sema;

import dev.superice.gdcc.frontend.parse.GdScriptParserService;
import dev.superice.gdcc.gdextension.ExtensionApiLoader;
import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdcc.scope.ClassRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrontendClassSkeletonTest {
    @Test
    void buildInjectsClassSkeletonsIntoRegistry() throws IOException {
        var parserService = new GdScriptParserService();
        var registry = new ClassRegistry(ExtensionApiLoader.loadDefault());
        var classSkeletonBuilder = new FrontendClassSkeletonBuilder();

        var baseSource = """
                class_name BaseClass
                extends RefCounted
                
                signal changed(value: int)
                var speed: float = 1.0
                
                func ping(x: int) -> int:
                    return x
                """;
        var childSource = """
                class_name ChildClass
                extends BaseClass
                
                var hp: int = 100
                
                func _ready():
                    pass
                """;
        var anonymousSource = """
                extends RefCounted
                
                var flag := true
                
                func tick():
                    pass
                """;

        var units = List.of(
                parserService.parseUnit(Path.of("tmp", "base_class.gd"), baseSource),
                parserService.parseUnit(Path.of("tmp", "child_class.gd"), childSource),
                parserService.parseUnit(Path.of("tmp", "no_name_script.gd"), anonymousSource)
        );

        var result = classSkeletonBuilder.build("test_module", units, registry);
        assertEquals(3, result.classDefs().size());

        var childClass = findClassByName(result.classDefs(), "ChildClass");
        assertEquals("BaseClass", childClass.getSuperName());
        assertEquals(1, childClass.getProperties().size());
        assertEquals("hp", childClass.getProperties().getFirst().getName());
        assertEquals(1, childClass.getFunctions().size());
        assertEquals("_ready", childClass.getFunctions().getFirst().getName());

        var baseClass = findClassByName(result.classDefs(), "BaseClass");
        assertEquals("RefCounted", baseClass.getSuperName());
        assertEquals(1, baseClass.getSignals().size());
        assertEquals("changed", baseClass.getSignals().getFirst().getName());

        var derivedNameClass = findClassByName(result.classDefs(), "NoNameScript");
        assertEquals("RefCounted", derivedNameClass.getSuperName());
        assertEquals(1, derivedNameClass.getProperties().size());
        assertEquals("flag", derivedNameClass.getProperties().getFirst().getName());

        assertNotNull(registry.findGdccClass("BaseClass"));
        assertNotNull(registry.findGdccClass("ChildClass"));
        assertNotNull(registry.findGdccClass("NoNameScript"));
    }

    private LirClassDef findClassByName(List<LirClassDef> classDefs, String className) {
        return classDefs.stream()
                .filter(classDef -> classDef.getName().equals(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Class not found: " + className));
    }
}
