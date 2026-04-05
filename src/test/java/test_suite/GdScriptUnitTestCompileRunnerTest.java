package test_suite;

import dev.superice.gdcc.backend.c.build.ZigUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class GdScriptUnitTestCompileRunnerTest {
    @Test
    void compilesAndValidatesBundledUnitScripts() throws Exception {
        if (ZigUtil.findZig() == null) {
            Assumptions.abort("Zig not found; skipping GDScript unit compile runner test");
            return;
        }

        var results = new GdScriptUnitTestCompileRunner().compileAndValidateAll();

        assertFalse(results.isEmpty(), "Expected at least one bundled unit-test case");
        assertEquals(
                "smoke/basic_arithmetic.gd",
                results.getFirst().scriptResourcePath(),
                () -> "Unexpected first unit-test script set: " + results
        );
    }
}
