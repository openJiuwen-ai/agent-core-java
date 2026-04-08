/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package examples.store;

import com.openjiuwen.core.foundation.store.object.LocalObjectStorageClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

final class StoreExampleSupport {

    private static final String SECTION_DIVIDER = "=".repeat(72);
    private static final String SUBSECTION_DIVIDER = "-".repeat(72);

    private static final Path EXAMPLE_ROOT = detectExampleRoot();
    private static final Path DATA_DIR = EXAMPLE_ROOT.resolve("data");
    private static final Path OUTPUT_DIR = resolvePathSetting(
            "openjiuwen.example.store.outputDir",
            "STORE_OUTPUT_DIR",
            EXAMPLE_ROOT.resolve("output")
    );
    private static final Path STORAGE_ROOT = resolvePathSetting(
            "openjiuwen.example.store.storageRoot",
            "STORE_STORAGE_ROOT",
            OUTPUT_DIR.resolve("local_object_storage")
    );
    private static final Path SOURCE_FILE = resolvePathSetting(
            "openjiuwen.example.store.sourceFile",
            "STORE_SOURCE_FILE",
            DATA_DIR.resolve("test.txt")
    );
    private static final Path DOWNLOAD_FILE = resolvePathSetting(
            "openjiuwen.example.store.downloadFile",
            "STORE_DOWNLOAD_FILE",
            OUTPUT_DIR.resolve("download").resolve("download_test.txt")
    );
    private static final String BUCKET_NAME = resolveStringSetting(
            "openjiuwen.example.store.bucketName",
            "STORE_BUCKET_NAME",
            "openjiuwen-local-demo"
    );
    private static final String OBJECT_NAME = resolveStringSetting(
            "openjiuwen.example.store.objectName",
            "STORE_OBJECT_NAME",
            "demo/test.txt"
    );
    private static final boolean KEEP_ARTIFACTS = Boolean.parseBoolean(resolveStringSetting(
            "openjiuwen.example.store.keepArtifacts",
            "STORE_KEEP_ARTIFACTS",
            "false"
    ));

    private StoreExampleSupport() {
    }

    static LocalObjectStorageClient createClient() {
        return new LocalObjectStorageClient(STORAGE_ROOT);
    }

    static void ensureRuntimeDirectories() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        Files.createDirectories(STORAGE_ROOT);
        Path downloadParent = DOWNLOAD_FILE.getParent();
        if (downloadParent != null) {
            Files.createDirectories(downloadParent);
        }
    }

    static void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    static void pruneEmptyDirectories(Path startPath, Path stopExclusive) throws IOException {
        Path current = startPath;
        while (current != null && !current.equals(stopExclusive) && Files.isDirectory(current)) {
            boolean hasChildren;
            try (Stream<Path> stream = Files.list(current)) {
                hasChildren = stream.findAny().isPresent();
            }
            if (hasChildren) {
                return;
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .forEach(currentPath -> {
                        try {
                            Files.deleteIfExists(currentPath);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    static boolean filesMatch(Path left, Path right) throws IOException {
        return Files.isRegularFile(left)
                && Files.isRegularFile(right)
                && Files.size(left) == Files.size(right)
                && Files.mismatch(left, right) == -1L;
    }

    static void section(String title) {
        line();
        line(SECTION_DIVIDER);
        line(title);
        line(SECTION_DIVIDER);
    }

    static void subsection(String title) {
        line();
        line(SUBSECTION_DIVIDER);
        line(title);
        line(SUBSECTION_DIVIDER);
    }

    static void line() {
        System.out.println();
    }

    static void line(String format, Object... args) {
        if (args == null || args.length == 0) {
            System.out.println(format);
            return;
        }
        System.out.println(String.format(Locale.ROOT, format, args));
    }

    static void keyValue(String label, Object value) {
        line("%-20s %s", label + ":", value);
    }

    static void printObjectList(List<Map<String, Object>> objects) {
        if (objects == null || objects.isEmpty()) {
            line("  (no objects found)");
            return;
        }
        for (Map<String, Object> object : objects) {
            line(
                    "  - bucket=%s object=%s size=%s",
                    object.getOrDefault("bucket", ""),
                    object.getOrDefault("object_name", ""),
                    object.getOrDefault("size", "")
            );
        }
    }

    static Path getExampleRoot() {
        return EXAMPLE_ROOT;
    }

    static Path getOutputDir() {
        return OUTPUT_DIR;
    }

    static Path getStorageRoot() {
        return STORAGE_ROOT;
    }

    static Path getSourceFile() {
        return SOURCE_FILE;
    }

    static Path getDownloadFile() {
        return DOWNLOAD_FILE;
    }

    static String getBucketName() {
        return BUCKET_NAME;
    }

    static String getObjectName() {
        return OBJECT_NAME;
    }

    static boolean keepArtifacts() {
        return KEEP_ARTIFACTS;
    }

    static Path getBucketPath() {
        return STORAGE_ROOT.resolve(BUCKET_NAME);
    }

    static Path getObjectPath() {
        return getBucketPath().resolve(OBJECT_NAME);
    }

    static String describePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(EXAMPLE_ROOT)) {
            String relativePath = EXAMPLE_ROOT.relativize(normalized).toString().replace('\\', '/');
            return relativePath.isEmpty() ? "." : relativePath;
        }
        return normalized.toString().replace('\\', '/');
    }

    private static Path detectExampleRoot() {
        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                currentDirectory.resolve("examples").resolve("store"),
                currentDirectory.resolve("agent-core-java-myfork").resolve("examples").resolve("store"),
                currentDirectory
        );
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (isStoreDirectory(normalized)) {
                return normalized;
            }
        }
        return candidates.get(0).toAbsolutePath().normalize();
    }

    private static boolean isStoreDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        Path fileName = path.getFileName();
        if (fileName != null && "store".equalsIgnoreCase(fileName.toString())) {
            return true;
        }
        return Files.isDirectory(path.resolve("data"));
    }

    private static Path resolvePathSetting(String propertyKey, String envKey, Path defaultValue) {
        String configuredValue = resolveOptionalStringSetting(propertyKey, envKey);
        if (configuredValue == null) {
            return defaultValue.toAbsolutePath().normalize();
        }
        return Path.of(configuredValue).toAbsolutePath().normalize();
    }

    private static String resolveStringSetting(String propertyKey, String envKey, String defaultValue) {
        String configuredValue = resolveOptionalStringSetting(propertyKey, envKey);
        return configuredValue == null ? defaultValue : configuredValue;
    }

    private static String resolveOptionalStringSetting(String propertyKey, String envKey) {
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        return null;
    }
}