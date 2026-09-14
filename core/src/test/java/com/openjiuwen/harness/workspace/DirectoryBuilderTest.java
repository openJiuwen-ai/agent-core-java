/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code DirectoryBuilder} in
 * {@code openjiuwen/harness/workspace/directory_builder.py}.
 */
class DirectoryBuilderTest {

    @TempDir
    private Path tempDir;

    @Test
    void buildCreatesMarkersAndDefaultFilesWithoutOverwriting() throws Exception {
        DirectoryBuilder builder = new DirectoryBuilder(tempDir.toString());
        List<Map<String, Object>> directories = List.of(Map.of(
                "path", "app",
                "children", List.of(Map.<String, Object>of(
                        "path", "README.md",
                        "is_file", true,
                        "default_content", "hello"
                ))
        ));

        builder.build(directories);

        Path appDir = tempDir.resolve("app");
        Path readme = appDir.resolve("README.md");
        assertTrue(Files.exists(appDir.resolve(".workspace")));
        assertEquals("hello", Files.readString(readme, StandardCharsets.UTF_8));

        Files.writeString(readme, "keep existing", StandardCharsets.UTF_8);
        builder.build(directories);
        assertEquals("keep existing", Files.readString(readme, StandardCharsets.UTF_8));
    }

    @Test
    void isSafePathRejectsAbsoluteAndTraversalPaths() {
        assertTrue(DirectoryBuilder.isSafePath(""));
        assertTrue(DirectoryBuilder.isSafePath("nested/child"));
        assertFalse(DirectoryBuilder.isSafePath("../secret"));
        assertFalse(DirectoryBuilder.isSafePath("/tmp/secret"));
        assertFalse(DirectoryBuilder.isSafePath("C:\\secret"));
        assertFalse(DirectoryBuilder.isSafePath("\\\\server\\share"));
    }

    @Test
    void buildRaisesOnUnsafePath() {
        DirectoryBuilder builder = new DirectoryBuilder(tempDir.toString());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.build(List.of(Map.of("path", "../secret")))
        );

        assertTrue(exception.getMessage().contains("Unsafe path detected"));
    }
}
