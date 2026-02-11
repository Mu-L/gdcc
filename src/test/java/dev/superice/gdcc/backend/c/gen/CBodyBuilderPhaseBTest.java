package dev.superice.gdcc.backend.c.gen;

import dev.superice.gdcc.backend.CodegenContext;
import dev.superice.gdcc.backend.ProjectInfo;
import dev.superice.gdcc.enums.GodotVersion;
import dev.superice.gdcc.gdextension.ExtensionAPI;
import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdcc.lir.LirFunctionDef;
import dev.superice.gdcc.lir.LirModule;
import dev.superice.gdcc.lir.LirVariable;
import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdcc.type.GdIntType;
import dev.superice.gdcc.type.GdType;
import dev.superice.gdcc.type.GdVoidType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CBodyBuilderPhaseBTest {
    private CBodyBuilder builder;
    private LirVariable mockVar;
    private LirVariable mockRefVar;

    @BeforeEach
    void setUp() {
        // Prepare mocks
        var projectInfo = new ProjectInfo("TestProject", GodotVersion.V451, Path.of(".")) {
            // Anonymous subclass
        };
        var extensionAPI = new ExtensionAPI(
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        var classRegistry = new ClassRegistry(extensionAPI);
        var ctx = new CodegenContext(projectInfo, classRegistry);
        var lirClassDef = new LirClassDef("TestClass", "RefCounted", false, false, Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        var lirFunctionDef = new LirFunctionDef("testFunc", false, false, false, false, false, Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), GdVoidType.VOID, Collections.emptyMap(), new LinkedHashMap<>());

        var helper = new CGenHelper(ctx, List.of(lirClassDef));

        builder = new CBodyBuilder(helper, lirClassDef, lirFunctionDef);

        // Mock variables
        mockVar = new LirVariable("v1", GdIntType.INT, lirFunctionDef);
        mockRefVar = new LirVariable("r1", GdIntType.INT, true, lirFunctionDef);
    }

    @Test
    void testAssignVar() {
        var target = builder.targetOfVar(mockVar);
        var value = builder.valueOfExpr("10", GdIntType.INT);

        builder.assignVar(target, value);

        assertEquals("$v1 = 10;\n", builder.build());
    }

    @Test
    void testCallVoid() {
        var arg1 = builder.valueOfExpr("1", GdIntType.INT);
        var arg2 = builder.valueOfVar(mockVar);

        builder.callVoid("some_func", List.of(arg1, arg2));

        assertEquals("some_func(1, $v1);\n", builder.build());
    }

    @Test
    void testCallAssign() {
        var target = builder.targetOfVar(mockVar);
        var arg1 = builder.valueOfExpr("1", GdIntType.INT);

        builder.callAssign(target, "some_func", List.of(arg1));

        assertEquals("$v1 = some_func(1);\n", builder.build());
    }

    @Test
    void testJump() {
        builder.jump("block_next");

        assertEquals("goto block_next;\n", builder.build());
    }

    @Test
    void testJumpIf() {
        var cond = builder.valueOfVar(mockVar);
        builder.jumpIf(cond, "block_true", "block_false");

        assertEquals("if ($v1) goto block_true;\nelse goto block_false;\n", builder.build());
    }

    @Test
    void testReturnVoid() {
        builder.returnVoid();

        assertEquals("return;\n", builder.build());
    }

    @Test
    void testReturnValue() {
        var val = builder.valueOfVar(mockVar);
        builder.returnValue(val);

        assertEquals("return $v1;\n", builder.build());
    }

    @Test
    void testTargetRefCheck() {
        assertThrows(RuntimeException.class, () -> builder.targetOfVar(mockRefVar));
    }
}
