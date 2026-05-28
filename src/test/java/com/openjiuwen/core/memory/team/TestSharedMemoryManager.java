/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.team;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SharedMemoryManager.
 * Mirrors Python's tests/unit_tests/core/memory/team/test_shared_memory_manager.py
 */
class TestSharedMemoryManager {

    private static final String TEAM_MEMORY_FILENAME = SharedMemoryManager.TEAM_MEMORY_FILENAME;
    private static final int TEAM_MEMORY_MAX_READ_LINES = SharedMemoryManager.TEAM_MEMORY_MAX_READ_LINES;

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("SharedMemoryManager tests")
    class ManagerTests {

        @Test
        @DisplayName("test read team summary empty file does not exist")
        void testReadTeamSummaryEmptyFileDoesNotExist() throws Exception {
            // Test read_team_summary returns empty string when file doesn't exist.
            SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

            String result = manager.readTeamSummary().get();

            assertEquals("", result);
        }

        @Test
        @DisplayName("test read team summary with content")
        void testReadTeamSummaryWithContent() throws Exception {
            // Test read_team_summary returns content when file exists.
            Path filePath = tempDir.resolve(TEAM_MEMORY_FILENAME);
            String testContent = "# Team Memory\n\n## Summary\nThis is a test team memory.";
            Files.writeString(filePath, testContent, StandardCharsets.UTF_8);

            SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

            String result = manager.readTeamSummary().get();

            assertTrue(result.contains("# Team Memory"));
        }

        @Test
        @DisplayName("test read team summary respects max lines")
        void testReadTeamSummaryRespectsMaxLines() throws Exception {
            // Test read_team_summary respects TEAM_MEMORY_MAX_READ_LINES limit.
            Path filePath = tempDir.resolve(TEAM_MEMORY_FILENAME);
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < TEAM_MEMORY_MAX_READ_LINES + 10; i++) {
                lines.add("Line " + i);
            }
            Files.write(filePath, lines, StandardCharsets.UTF_8);

            SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

            String result = manager.readTeamSummary().get();

            String[] resultLines = result.split("\n");
            assertTrue(resultLines.length <= TEAM_MEMORY_MAX_READ_LINES);
        }

        @Test
        @DisplayName("test write team summary creates file")
        void testWriteTeamSummaryCreatesFile() throws Exception {
            // Test write_team_summary creates the file.
            String testContent = "# New Team Memory\n\nWritten content.";

            SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());
            manager.writeTeamSummary(testContent).get();

            Path filePath = tempDir.resolve(TEAM_MEMORY_FILENAME);
            assertTrue(Files.exists(filePath));

            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            assertTrue(content.contains("# New Team Memory"));
        }

        @Test
        @DisplayName("test write team summary overwrites existing")
        void testWriteTeamSummaryOverwritesExisting() throws Exception {
            // Test write_team_summary overwrites existing content.
            Path filePath = tempDir.resolve(TEAM_MEMORY_FILENAME);
            Files.writeString(filePath, "Original content", StandardCharsets.UTF_8);

            SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());
            manager.writeTeamSummary("New content").get();

            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            assertEquals("New content", content);
        }

        @Test
        @DisplayName("test append entry first entry")
        void testAppendEntryFirstEntry() throws Exception {
            // Test append_entry with no existing content.
            SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());
            manager.appendEntry("First entry").get();

            String result = manager.readTeamSummary().get();
            assertTrue(result.contains("First entry"));
        }

        @Test
        @DisplayName("test append entry adds separator")
        void testAppendEntryAddsSeparator() throws Exception {
            // Test append_entry adds separator between entries.
            SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());
            manager.appendEntry("First entry").get();
            manager.appendEntry("Second entry").get();

            String result = manager.readTeamSummary().get();
            assertTrue(result.contains("First entry"));
            assertTrue(result.contains("Second entry"));
            assertTrue(result.contains("---"));
        }

        @Test
        @DisplayName("test ensure dir creates directory")
        void testEnsureDirCreatesDirectory() throws Exception {
            // Test ensure_dir creates the directory if it doesn't exist.
            Path teamDir = tempDir.resolve("new_team_dir");
            SharedMemoryManager manager = new SharedMemoryManager(teamDir.toString());

            assertFalse(Files.exists(teamDir));
            manager.ensureDir().get();
            assertTrue(Files.exists(teamDir));
        }

        @Test
        @DisplayName("test write to nested directory")
        void testWriteToNestedDirectory() throws Exception {
            // Test write_team_summary works with nested directory path.
            Path nestedDir = tempDir.resolve("level1").resolve("level2").resolve("team_memory");
            String testContent = "# Nested team memory";

            SharedMemoryManager manager = new SharedMemoryManager(nestedDir.toString());
            manager.writeTeamSummary(testContent).get();

            Path filePath = nestedDir.resolve(TEAM_MEMORY_FILENAME);
            assertTrue(Files.exists(filePath));

            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            assertTrue(content.contains(testContent));
        }

        @Test
        @DisplayName("test read empty file returns empty string")
        void testReadEmptyFileReturnsEmptyString() throws Exception {
            // Test read_team_summary returns empty string for empty file.
            Path filePath = tempDir.resolve(TEAM_MEMORY_FILENAME);
            Files.writeString(filePath, "", StandardCharsets.UTF_8);

            SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

            String result = manager.readTeamSummary().get();

            assertEquals("", result);
        }

        @Test
        @DisplayName("test append after read")
        void testAppendAfterRead() throws Exception {
            // Test append after reading preserves original content.
            String originalContent = "# Original\n\nThis is original content.";
            SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());
            manager.writeTeamSummary(originalContent).get();

            manager.appendEntry("New entry").get();

            String result = manager.readTeamSummary().get();
            assertTrue(result.contains("Original"));
            assertTrue(result.contains("New entry"));
        }

        @Test
        @DisplayName("test concurrent writes yield single complete payload")
        void testConcurrentWritesYieldSingleCompletePayload() throws Exception {
            // Concurrent writes: final file equals one full payload (no truncated hybrid).
            SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

            List<String> payloads = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                payloads.add("FULL-" + i + "-" + "x".repeat(400));
            }

            // Execute all writes concurrently
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (String p : payloads) {
                futures.add(manager.writeTeamSummary(p));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            Path filePath = tempDir.resolve(TEAM_MEMORY_FILENAME);
            String body = Files.readString(filePath, StandardCharsets.UTF_8);

            assertTrue(payloads.contains(body));
        }

        @Test
        @DisplayName("test write team summary creates nested team memory dir")
        void testWriteTeamSummaryCreatesNestedTeamMemoryDir() throws Exception {
            // Parent path exists but team-memory leaf does not: write creates directory then file.
            Path parent = tempDir.resolve("exists");
            Files.createDirectories(parent);
            Path nestedTeam = parent.resolve("nested").resolve("team-memory");

            assertFalse(Files.exists(nestedTeam));

            SharedMemoryManager manager = new SharedMemoryManager(nestedTeam.toString());
            manager.writeTeamSummary("nested ok").get();

            assertTrue(Files.isDirectory(nestedTeam));
            Path target = nestedTeam.resolve(TEAM_MEMORY_FILENAME);
            assertTrue(Files.isRegularFile(target));
            assertEquals("nested ok", Files.readString(target, StandardCharsets.UTF_8));
        }
    }
}