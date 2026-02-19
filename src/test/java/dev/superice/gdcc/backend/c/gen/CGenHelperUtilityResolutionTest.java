package dev.superice.gdcc.backend.c.gen;

import dev.superice.gdcc.backend.CodegenContext;
import dev.superice.gdcc.backend.ProjectInfo;
import dev.superice.gdcc.enums.GodotVersion;
import dev.superice.gdcc.gdextension.ExtensionAPI;
import dev.superice.gdcc.gdextension.ExtensionFunctionArgument;
import dev.superice.gdcc.gdextension.ExtensionUtilityFunction;
import dev.superice.gdcc.scope.ClassRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CGenHelperUtilityResolutionTest {
    @Test
    @DisplayName("normalizeUtilityLookupName should keep unprefixed names")
    void normalizeUtilityLookupNameKeepsUnprefixedNames() {
        var helper = newHelper();
        assertEquals("print", helper.normalizeUtilityLookupName("print"));
    }

    @Test
    @DisplayName("normalizeUtilityLookupName should strip godot_ prefix")
    void normalizeUtilityLookupNameStripsPrefix() {
        var helper = newHelper();
        assertEquals("print", helper.normalizeUtilityLookupName("godot_print"));
    }

    @Test
    @DisplayName("toUtilityCFunctionName should always return godot-prefixed symbol")
    void toUtilityCFunctionNameAlwaysReturnsPrefixedSymbol() {
        var helper = newHelper();
        assertEquals("godot_print", helper.toUtilityCFunctionName("print"));
        assertEquals("godot_print", helper.toUtilityCFunctionName("godot_print"));
    }

    @Test
    @DisplayName("resolveUtilityCall should resolve unprefixed utility")
    void resolveUtilityCallUnprefixed() {
        var helper = newHelper();
        var utility = helper.resolveUtilityCall("print");

        assertNotNull(utility);
        assertEquals("print", utility.lookupName());
        assertEquals("godot_print", utility.cFunctionName());
        assertTrue(utility.signature().isVararg());
    }

    @Test
    @DisplayName("resolveUtilityCall should resolve prefixed utility")
    void resolveUtilityCallPrefixed() {
        var helper = newHelper();
        var utility = helper.resolveUtilityCall("godot_deg_to_rad");

        assertNotNull(utility);
        assertEquals("deg_to_rad", utility.lookupName());
        assertEquals("godot_deg_to_rad", utility.cFunctionName());
        assertEquals("float", utility.signature().returnType().getTypeName());
    }

    @Test
    @DisplayName("resolveUtilityCall should return null for missing utility")
    void resolveUtilityCallMissing() {
        var helper = newHelper();
        assertNull(helper.resolveUtilityCall("missing_utility"));
    }

    private CGenHelper newHelper() {
        var projectInfo = new ProjectInfo("TestProject", GodotVersion.V451, Path.of(".")) {
        };
        var context = new CodegenContext(projectInfo, new ClassRegistry(utilityApi()));
        return new CGenHelper(context, List.of());
    }

    private ExtensionAPI utilityApi() {
        return new ExtensionAPI(
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new ExtensionUtilityFunction(
                                "print",
                                null,
                                "general",
                                true,
                                0,
                                List.of(new ExtensionFunctionArgument("arg1", "Variant", null, null))
                        ),
                        new ExtensionUtilityFunction(
                                "deg_to_rad",
                                "float",
                                "math",
                                false,
                                2140049587,
                                List.of(new ExtensionFunctionArgument("deg", "float", null, null))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
