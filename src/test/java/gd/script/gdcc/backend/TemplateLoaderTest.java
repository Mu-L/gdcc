package gd.script.gdcc.backend;

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

    @Test
    public void trimNumericRemovesPrecedingSpaces() throws IOException, TemplateException {
        var tpl = "line1\n    __trim<3>__value\n"; // line2 has 4 leading spaces; __trim<3>__ should remove 3 of them, leaving 1
        var out = TemplateLoader.processTrimMarkers(tpl);
        var expected = "line1\n value\n"; // note: one leading space left
        assertEquals(expected, out);
    }

    @Test
    public void trimPlainMatchesPreviousLineIndent() throws IOException, TemplateException {
        var tpl = "prev\n    line\n__trim__aligned\n"; // previous line (line2) has 4 leading spaces; line3 should be adjusted to 4
        var out = TemplateLoader.processTrimMarkers(tpl);
        var expected = "prev\n    line\n    aligned\n";
        assertEquals(expected, out);
    }
}
