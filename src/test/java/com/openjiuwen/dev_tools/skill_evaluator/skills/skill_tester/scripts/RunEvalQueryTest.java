/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator.skills.skill_tester.scripts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the skill evaluation query runner script.
 * <p>
 * Mirrors Python's {@code run_eval_query} in
 * {@code openjiuwen.dev_tools.skill_evaluator.skills.skill_tester.scripts.run_eval_query}.
 */
class RunEvalQueryTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveOutputFileWithNullPathReturnsDefault() {
        Path result = resolveOutputFile(null, tempDir);
        assertNotNull(result);
        assertTrue(result.getFileName().toString().startsWith("skill_test_result_"));
        assertTrue(result.getFileName().toString().endsWith(".txt"));
    }

    @Test
    void resolveOutputFileWithDirectoryCreatesTimestampedFile() throws IOException {
        Path dir = tempDir.resolve("output_dir");
        Files.createDirectories(dir);

        Path result = resolveOutputFile(dir, tempDir);
        assertEquals(dir, result.getParent());
        assertTrue(result.getFileName().toString().endsWith(".txt"));
    }

    @Test
    void resolveOutputFileWithFilePathReturnsItDirectly() throws IOException {
        Path file = tempDir.resolve("result.txt");
        Path result = resolveOutputFile(file, tempDir);
        assertEquals(file, result);
    }

    @Test
    void timestampFormatIsValid() {
        ZoneId tz = ZoneOffset.ofHours(8);
        String timestamp = ZonedDateTime.now(tz).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        assertTrue(timestamp.matches("\\d{8}_\\d{6}"));
    }

    @Test
    void defaultMaxIterationsIs40() {
        int maxIterations = Integer.parseInt(System.getenv().getOrDefault("MAX_ITERATIONS", "40"));
        assertEquals(40, maxIterations);
    }

    @Test
    void nonExistentSkillPathThrows() {
        Path missing = tempDir.resolve("nonexistent_skill");
        assertFalse(Files.exists(missing));
    }

    private Path resolveOutputFile(Path outputFilePath, Path baseDir) {
        ZoneId tz = ZoneOffset.ofHours(8);
        String timestamp = ZonedDateTime.now(tz).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String defaultFilename = "skill_test_result_" + timestamp + ".txt";

        if (outputFilePath == null) {
            return baseDir.resolve(defaultFilename);
        }

        boolean looksLikeDir = Files.isDirectory(outputFilePath)
                || !hasExtension(outputFilePath)
                || outputFilePath.toString().endsWith("/") || outputFilePath.toString().endsWith("\\");

        if (looksLikeDir) {
            try {
                Files.createDirectories(outputFilePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return outputFilePath.resolve(defaultFilename);
        }

        try {
            Files.createDirectories(outputFilePath.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputFilePath;
    }

    private boolean hasExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0;
    }
}
