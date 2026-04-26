package gd.script.gdcc.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GdccVersionTest {
    private static final Pattern GRADLE_VERSION_PATTERN = Pattern.compile("(?m)^version\\s*=\\s*\"([^\"]+)\"");

    @Test
    void currentReadsGeneratedMetadataAndCachesIt() throws IOException {
        var expectedVersion = requireGradleProjectVersion();
        var first = GdccVersion.current();
        var second = GdccVersion.current();

        assertSame(first, second);
        assertAll(
                () -> assertEquals(expectedVersion, first.version()),
                () -> assertFalse(first.branch().isBlank()),
                () -> assertTrue(first.commit().matches("unknown|[0-9a-fA-F]{7,}"), first.commit())
        );
    }

    @Test
    void displayTextIncludesVersionBranchAndCommit() {
        var info = GdccVersion.current();

        assertEquals("gdcc " + info.version() + " (" + info.branch() + " " + info.commit() + ")", GdccVersion.displayText());
    }

    private static String requireGradleProjectVersion() throws IOException {
        var matcher = GRADLE_VERSION_PATTERN.matcher(Files.readString(Path.of("build.gradle.kts")));
        assertTrue(matcher.find(), "build.gradle.kts must declare project version");
        return matcher.group(1);
    }
}
