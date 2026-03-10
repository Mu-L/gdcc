package dev.superice.gdcc.frontend.sema;

import dev.superice.gdcc.frontend.parse.GdScriptParserService;
import dev.superice.gdcc.gdextension.ExtensionApiLoader;
import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdcc.lir.LirPropertyDef;
import dev.superice.gdcc.scope.ClassRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendClassSkeletonAnnotationTest {
    @Test
    void buildPreservesExportAndOnreadyPropertyAnnotationsFromLeadingAnnotations() throws Exception {
        var parserService = new GdScriptParserService();
        var registry = new ClassRegistry(ExtensionApiLoader.loadDefault());
        var classSkeletonBuilder = new FrontendClassSkeletonBuilder();
        var unit = parserService.parseUnit(Path.of("tmp", "annotated_props.gd"), """
                class_name AnnotatedProps
                extends Node
                
                @export var hp: int = 1
                @onready var target = $Node
                var plain := 3
                """);

        var result = classSkeletonBuilder.build("test_module", List.of(unit), registry);
        var classDef = findClassByName(result.classDefs(), "AnnotatedProps");
        var hpProperty = findPropertyByName(classDef, "hp");
        var targetProperty = findPropertyByName(classDef, "target");
        var plainProperty = findPropertyByName(classDef, "plain");

        assertEquals("", hpProperty.getAnnotations().get("export"));
        assertFalse(hpProperty.getAnnotations().containsKey("onready"));

        assertEquals("", targetProperty.getAnnotations().get("onready"));
        assertFalse(targetProperty.getAnnotations().containsKey("export"));

        assertTrue(plainProperty.getAnnotations().isEmpty());
    }

    @Test
    void buildIgnoresRegionAndUnrelatedAnnotationsForPropertyRetention() throws Exception {
        var parserService = new GdScriptParserService();
        var registry = new ClassRegistry(ExtensionApiLoader.loadDefault());
        var classSkeletonBuilder = new FrontendClassSkeletonBuilder();
        var unit = parserService.parseUnit(Path.of("tmp", "ignored_annotations.gd"), """
                class_name IgnoredAnnotations
                extends Node
                
                @warning_ignore_start("unused_variable")
                var tmp := 1
                
                @warning_ignore_restore("unused_variable")
                var keep := 2
                
                @rpc("authority")
                func ping(value):
                    pass
                
                var after := 3
                """);

        var result = classSkeletonBuilder.build("test_module", List.of(unit), registry);
        var classDef = findClassByName(result.classDefs(), "IgnoredAnnotations");

        assertTrue(findPropertyByName(classDef, "tmp").getAnnotations().isEmpty());
        assertTrue(findPropertyByName(classDef, "keep").getAnnotations().isEmpty());
        assertTrue(findPropertyByName(classDef, "after").getAnnotations().isEmpty());
    }

    private LirClassDef findClassByName(List<LirClassDef> classDefs, String className) {
        return classDefs.stream()
                .filter(classDef -> classDef.getName().equals(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Class not found: " + className));
    }

    private LirPropertyDef findPropertyByName(LirClassDef classDef, String propertyName) {
        return classDef.getProperties().stream()
                .filter(propertyDef -> propertyDef.getName().equals(propertyName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Property not found: " + propertyName));
    }
}
