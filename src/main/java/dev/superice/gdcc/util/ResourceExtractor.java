package dev.superice.gdcc.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ResourceExtractor {

    private ResourceExtractor() {
        // utility
    }

    /// Extracts all resources under the given resource folder path (relative to classpath root)
    /// into the provided target directory.
    /// If a resource is a zip file (ends with .zip) it will be expanded into a directory named
    /// after the zip (without the .zip extension). Existing files are not removed. When writing
    /// a file that already exists the extractor compares contents; if identical it skips writing.
    /// If different it writes to a temporary sibling file and then atomically replaces the original.
    public static void extract(@NotNull String resourceFolderPath, @NotNull Path targetDir, @NotNull ClassLoader loader) throws IOException {
        Objects.requireNonNull(resourceFolderPath);
        Objects.requireNonNull(targetDir);
        Objects.requireNonNull(loader);

        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        var roots = loader.getResources(resourceFolderPath);
        var seen = new HashMap<URI, Boolean>();
        while (roots.hasMoreElements()) {
            var url = roots.nextElement();
            var uri = toUri(url);
            // avoid processing same root twice
            if (seen.putIfAbsent(uri, Boolean.TRUE) != null) continue;

            switch (url.getProtocol()) {
                case "file" -> extractFromFile(url, targetDir);
                case "jar" -> extractFromJar(url, targetDir);
                default -> throw new IOException("Cannot list resources for protocol: " + url.getProtocol());
            }
        }
    }

    /// Extract specific resources under resourceFolderPath. The list is checked first: if any
    /// requested resource does not exist, the method throws and refuses to extract any of them.
    public static void extractSpecific(@NotNull String resourceFolderPath, @NotNull List<String> specificResourceList, @NotNull Path targetDir, @NotNull ClassLoader loader) throws IOException {
        Objects.requireNonNull(resourceFolderPath);
        Objects.requireNonNull(specificResourceList);
        Objects.requireNonNull(targetDir);
        Objects.requireNonNull(loader);

        if (!Files.exists(targetDir)) Files.createDirectories(targetDir);

        // first, resolve all resources and ensure they exist
        var resolved = new HashMap<String, URL>();
        for (var name : specificResourceList) {
            var resPath = resourceFolderPath + "/" + name;
            var url = loader.getResource(resPath);
            if (url == null) {
                throw new IOException("Requested resource not found: " + resPath);
            }
            resolved.put(name, url);
        }

        // all exist: now extract each
        for (var e : resolved.entrySet()) {
            var name = e.getKey();
            var url = e.getValue();
            var out = targetDir.resolve(name);
            switch (url.getProtocol()) {
                case "file" -> {
                    var source = Paths.get(toUri(url));
                    if (name.endsWith(".zip")) {
                        var dir = out.getParent() == null ? out.resolveSibling(stripZipExt(name)) : out.getParent().resolve(stripZipExt(name));
                        Files.createDirectories(dir);
                        try (var is = Files.newInputStream(source)) {
                            unzipStream(is, dir);
                        }
                    } else {
                        Files.createDirectories(out.getParent());
                        writeFileWithAtomicReplace(source, out);
                    }
                }
                case "jar" -> {
                    var resPath = resourceFolderPath + "/" + name;
                    try (var is = loader.getResourceAsStream(resPath)) {
                        if (is == null) throw new IOException("Resource disappeared: " + resPath);
                        copyStreamOrUnpack(is, name, out);
                    }
                }
                default -> throw new IOException("Unsupported protocol for resource: " + url.getProtocol());
            }
        }
    }

    private static URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (Exception e) {
            try {
                return new URI(url.toString());
            } catch (Exception ex) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void extractFromFile(URL url, Path targetDir) throws IOException {
        try {
            var rootPath = Paths.get(toUri(url));
            if (!Files.exists(rootPath)) return;
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    var rel = rootPath.relativize(file);
                    var out = targetDir.resolve(rel.toString());
                    copyOrUnpack(file, out);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(rootPath)) {
                        var rel = rootPath.relativize(dir);
                        Files.createDirectories(targetDir.resolve(rel.toString()));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static void extractFromJar(URL url, Path targetDir) throws IOException {
        var conn = (JarURLConnection) url.openConnection();
        var entryName = conn.getEntryName();
        if (entryName == null) throw new IOException("Cannot determine jar entry for URL: " + url);
        try (var jarFs = FileSystems.newFileSystem(conn.getJarFileURL().toURI(), Map.of())) {
            var pathInJar = jarFs.getPath("/" + entryName);
            if (!Files.exists(pathInJar)) return;
            Files.walkFileTree(pathInJar, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    var rel = pathInJar.relativize(file);
                    var out = targetDir.resolve(rel.toString());
                    // create parent dirs
                    Files.createDirectories(out.getParent());
                    try (var is = Files.newInputStream(file)) {
                        copyStreamOrUnpack(is, file.getFileName().toString(), out);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(pathInJar)) {
                        var rel = pathInJar.relativize(dir);
                        Files.createDirectories(targetDir.resolve(rel.toString()));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static void copyOrUnpack(Path sourceFile, Path out) throws IOException {
        var fileName = sourceFile.getFileName().toString();
        if (fileName.endsWith(".zip")) {
            // unzip into directory named after zip (without .zip)
            var dir = out.getParent() == null ? out.resolveSibling(stripZipExt(fileName)) : out.getParent().resolve(stripZipExt(fileName));
            Files.createDirectories(dir);
            try (var is = Files.newInputStream(sourceFile)) {
                unzipStream(is, dir);
            }
        } else {
            Files.createDirectories(out.getParent());
            writeFileWithAtomicReplace(sourceFile, out);
        }
    }

    private static void copyStreamOrUnpack(InputStream is, String resourceName, Path out) throws IOException {
        if (resourceName.endsWith(".zip")) {
            var dir = out.getParent() == null ? out.resolveSibling(stripZipExt(resourceName)) : out.getParent().resolve(stripZipExt(resourceName));
            Files.createDirectories(dir);
            unzipStream(is, dir);
        } else {
            Files.createDirectories(out.getParent());
            // write stream to temp then atomic replace
            var tmp = Files.createTempFile(out.getParent(), ".gdcc-extract-", ".tmp");
            try (var os = Files.newOutputStream(tmp)) {
                is.transferTo(os);
            }
            atomicReplace(tmp, out);
        }
    }

    private static void unzipStream(InputStream is, Path dir) throws IOException {
        try (var zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(dir.resolve(entry.getName()));
                } else {
                    var out = dir.resolve(entry.getName());
                    Files.createDirectories(out.getParent());
                    var tmp = Files.createTempFile(out.getParent(), ".gdcc-unzip-", ".tmp");
                    try (var os = Files.newOutputStream(tmp)) {
                        zis.transferTo(os);
                    }
                    atomicReplace(tmp, out);
                }
                zis.closeEntry();
            }
        }
    }

    private static void writeFileWithAtomicReplace(Path sourceFile, Path out) throws IOException {
        if (Files.exists(out)) {
            // compare
            var mismatch = Files.mismatch(sourceFile, out);
            if (mismatch == -1L) return; // identical
        }
        // write to tmp in same dir then replace
        var tmp = Files.createTempFile(out.getParent(), ".gdcc-copy-", ".tmp");
        try (var is = Files.newInputStream(sourceFile); var os = Files.newOutputStream(tmp)) {
            is.transferTo(os);
        }
        atomicReplace(tmp, out);
    }

    private static void atomicReplace(Path tmp, Path out) throws IOException {
        try {
            Files.move(tmp, out, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // fallback
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String stripZipExt(String name) {
        if (name.toLowerCase().endsWith(".zip")) return name.substring(0, name.length() - 4);
        return name;
    }
}
