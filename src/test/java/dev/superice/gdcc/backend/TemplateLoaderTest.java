package dev.superice.gdcc.backend;

import freemarker.template.TemplateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TemplateLoaderTest {

    @Test
    public void rendersClasspathTemplateWithIncludeAndImport() throws IOException, TemplateException {
        // ensure Map has Object values to match TemplateLoader API
        Map<String, Object> ctx = Map.of("name", "World");
        var out = TemplateLoader.renderFromClasspath("template/main.ftl", ctx);

        // expected pieces
        assertTrue(out.contains("Hello World!"), "Should contain greeting");
        assertTrue(out.contains("This is partial."), "Should include partial file");
        assertTrue(out.contains("WORLD!!!"), "Should import macros and shout");

        // trimmed exact check (order preserved)
        var expected = "Hello World!\nThis is partial.\nWORLD!!!\n";
        var normExpected = expected.replace("\r\n", "\n").strip();
        var normOut = out.replace("\r\n", "\n").strip();
        assertEquals(normExpected, normOut);
    }
}
