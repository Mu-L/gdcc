package dev.superice.gdcc.backend.c.gen;

import dev.superice.gdcc.backend.CodegenContext;
import dev.superice.gdcc.backend.ProjectInfo;
import dev.superice.gdcc.enums.GodotVersion;
import dev.superice.gdcc.gdextension.ExtensionAPI;
import dev.superice.gdcc.gdextension.ExtensionEnumValue;
import dev.superice.gdcc.gdextension.ExtensionGlobalEnum;
import dev.superice.gdcc.gdextension.ExtensionGdClass;
import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdcc.lir.LirFunctionDef;
import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdcc.type.GdVoidType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests for StringName/String static pointer literal ValueRefs.
public class CBodyBuilderLiteralValueTest {
    private CBodyBuilder builder;

    @BeforeEach
    void setUp() {
        var projectInfo = new ProjectInfo("TestProject", GodotVersion.V451, Path.of(".")) {
            // Anonymous subclass to bypass abstract
        };

        var refCountedClass = new ExtensionGdClass(
                "RefCounted", true, true, "Object", "core",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()
        );
        var extensionAPI = new ExtensionAPI(
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(new ExtensionGlobalEnum(
                        "TestEnum",
                        false,
                        List.of(new ExtensionEnumValue("VALUE_A", 42))
                )),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(refCountedClass),
                Collections.emptyList(),
                Collections.emptyList()
        );
        var classRegistry = new ClassRegistry(extensionAPI);

        var ctx = new CodegenContext(projectInfo, classRegistry);
        var lirClassDef = new LirClassDef("TestClass", "RefCounted", false, false,
                Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        var lirFunctionDef = new LirFunctionDef("testFunc", false, false, false, false, false,
                Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(),
                GdVoidType.VOID, Collections.emptyMap(), new LinkedHashMap<>());

        var helper = new CGenHelper(ctx, List.of(lirClassDef));
        builder = new CBodyBuilder(helper, lirClassDef, lirFunctionDef);
    }

    @Test
    @DisplayName("StringName pointer literal should render GD_STATIC_SN without address-of")
    void testStringNamePtrLiteralValue() {
        var value = builder.valueOfStringNamePtrLiteral("MyClass");

        builder.callVoid("func", List.of(value));

        assertEquals("func(GD_STATIC_SN(u8\"MyClass\"));\n", builder.build());
    }

    @Test
    @DisplayName("String pointer literal should render GD_STATIC_S without address-of")
    void testStringPtrLiteralValue() {
        var value = builder.valueOfStringPtrLiteral("RefCounted");

        builder.callVoid("func", List.of(value));

        assertEquals("func(GD_STATIC_S(u8\"RefCounted\"));\n", builder.build());
    }

    @Test
    @DisplayName("StringName pointer literal should escape special characters")
    void testStringNamePtrLiteralEscapesSpecialChars() {
        var value = "line1\nline2\t\"quote\"\\snow\u2603\uD83D\uDE00";
        var expected = "func(GD_STATIC_SN(u8\"line1\\nline2\\t\\\"quote\\\"\\\\snow\\u2603\\U0001F600\"));\n";

        builder.callVoid("func", List.of(builder.valueOfStringNamePtrLiteral(value)));

        assertEquals(expected, builder.build());
    }

    @Test
    @DisplayName("String pointer literal should escape special characters")
    void testStringPtrLiteralEscapesSpecialChars() {
        var value = "tab\tcarriage\rnewline\nbackslash\\quote\"";
        var expected = "func(GD_STATIC_S(u8\"tab\\tcarriage\\rnewline\\nbackslash\\\\quote\\\"\"));\n";

        builder.callVoid("func", List.of(builder.valueOfStringPtrLiteral(value)));

        assertEquals(expected, builder.build());
    }

    @Test
    @DisplayName("TempVar should act as both target and value reference")
    void testTempVarAsTargetAndValue() {
        var temp = builder.newTempVariable("string", dev.superice.gdcc.type.GdStringType.STRING,
                "godot_new_String_with_utf8_chars(u8\"init\")");

        builder.declareTempVar(temp);
        builder.callAssign(temp, "make_string", dev.superice.gdcc.type.GdStringType.STRING, List.of());
        builder.callVoid("use_string", List.of(temp));
        builder.destroyTempVar(temp);

        var expected = "godot_String __gdcc_tmp_string_0 = godot_new_String_with_utf8_chars(u8\"init\");\n"
                + "godot_String_destroy(&__gdcc_tmp_string_0);\n"
                + "__gdcc_tmp_string_0 = make_string();\n"
                + "use_string(&__gdcc_tmp_string_0);\n"
                + "godot_String_destroy(&__gdcc_tmp_string_0);\n";
        assertEquals(expected, builder.build());
    }
}
