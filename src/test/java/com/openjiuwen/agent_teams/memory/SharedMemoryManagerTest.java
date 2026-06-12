/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.memory.TeamMemoryExtractor.FileEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link SharedMemoryManager}.
 *
 * <p>Mirrors Python's {@code test_shared_memory_manager.py} for
 * {@code openjiuwen/agent_teams/memory/shared_memory.py}.</p>
 */
class SharedMemoryManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void readTeamSummaryReturnsEmptyWhenFileDoesNotExist() {
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

        assertThat(await(manager.readTeamSummaryText())).isEmpty();
        assertThat(await(manager.readTeamSummary())).isEmpty();
    }

    @Test
    void readTeamSummaryReturnsContentWhenFileExists() throws IOException {
        String testContent = "# Team Memory\n\n## Summary\nThis is a test team memory.";
        Files.writeString(tempDir.resolve(SharedMemoryManager.TEAM_MEMORY_FILENAME), testContent);
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

        String result = await(manager.readTeamSummaryText());

        assertThat(result).contains(testContent);
    }

    @Test
    void readTeamSummaryRespectsMaxLines() throws IOException {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < SharedMemoryManager.TEAM_MEMORY_MAX_READ_LINES + 10; i++) {
            lines.add("Line " + i);
        }
        Files.writeString(tempDir.resolve(SharedMemoryManager.TEAM_MEMORY_FILENAME), String.join("\n", lines));
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

        String result = await(manager.readTeamSummaryText());

        assertThat(result.split("\n")).hasSizeLessThanOrEqualTo(SharedMemoryManager.TEAM_MEMORY_MAX_READ_LINES);
    }

    @Test
    void writeTeamSummaryCreatesFile() throws IOException {
        String testContent = "# New Team Memory\n\nWritten content.";
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

        await(manager.writeTeamSummary(testContent));

        Path target = tempDir.resolve(SharedMemoryManager.TEAM_MEMORY_FILENAME);
        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.readString(target)).contains(testContent);
    }

    @Test
    void writeTeamSummaryOverwritesExisting() throws IOException {
        Path target = tempDir.resolve(SharedMemoryManager.TEAM_MEMORY_FILENAME);
        Files.writeString(target, "Original content");
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

        await(manager.writeTeamSummary("New content"));

        assertThat(Files.readString(target)).isEqualTo("New content");
    }

    @Test
    void appendEntryFirstEntryCreatesFile() {
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

        await(manager.appendEntry("First entry"));

        assertThat(await(manager.readTeamSummaryText())).contains("First entry");
    }

    @Test
    void appendEntryAddsSeparator() {
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

        await(manager.appendEntry("First entry"));
        await(manager.appendEntry("Second entry"));

        String result = await(manager.readTeamSummaryText());
        assertThat(result).contains("First entry");
        assertThat(result).contains("Second entry");
        assertThat(result).contains("---");
    }

    @Test
    void ensureDirCreatesDirectory() {
        Path teamDir = tempDir.resolve("new_team_dir");
        SharedMemoryManager manager = new SharedMemoryManager(teamDir.toString());

        assertThat(Files.exists(teamDir)).isFalse();
        await(manager.ensureDir());

        assertThat(Files.exists(teamDir)).isTrue();
    }

    @Test
    void writeTeamSummaryWorksWithNestedDirectory() throws IOException {
        Path nestedDir = tempDir.resolve("level1").resolve("level2").resolve("team_memory");
        SharedMemoryManager manager = new SharedMemoryManager(nestedDir.toString());

        await(manager.writeTeamSummary("# Nested team memory"));

        Path target = nestedDir.resolve(SharedMemoryManager.TEAM_MEMORY_FILENAME);
        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.readString(target)).contains("# Nested team memory");
    }

    @Test
    void readEmptyFileReturnsEmptyString() throws IOException {
        Files.writeString(tempDir.resolve(SharedMemoryManager.TEAM_MEMORY_FILENAME), "");
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

        assertThat(await(manager.readTeamSummaryText())).isEmpty();
    }

    @Test
    void appendAfterReadPreservesOriginalContent() {
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());

        await(manager.writeTeamSummary("# Original\n\nThis is original content."));
        await(manager.appendEntry("New entry"));

        String result = await(manager.readTeamSummaryText());
        assertThat(result).contains("Original");
        assertThat(result).contains("New entry");
    }

    @Test
    void concurrentWritesYieldSingleCompletePayload() throws IOException {
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString());
        List<String> payloads = new ArrayList<>();
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            String payload = "FULL-" + i + "-" + "x".repeat(400);
            payloads.add(payload);
            writes.add(CompletableFuture.runAsync(() -> await(manager.writeTeamSummary(payload))));
        }

        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();

        String body = Files.readString(tempDir.resolve(SharedMemoryManager.TEAM_MEMORY_FILENAME));
        assertThat(payloads).contains(body);
    }

    @Test
    void writeTeamSummaryCreatesNestedTeamMemoryDir() throws IOException {
        Path parent = tempDir.resolve("exists");
        Files.createDirectories(parent);
        Path nestedTeam = parent.resolve("nested").resolve("team-memory");
        SharedMemoryManager manager = new SharedMemoryManager(nestedTeam.toString());

        await(manager.writeTeamSummary("nested ok"));

        assertThat(Files.isDirectory(nestedTeam)).isTrue();
        assertThat(Files.readString(nestedTeam.resolve(SharedMemoryManager.TEAM_MEMORY_FILENAME)))
                .isEqualTo("nested ok");
    }

    @Test
    void sysOperationReadAndWriteArePreferred() {
        FakeFileSystem fileSystem = new FakeFileSystem();
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString(), fileSystem);

        await(manager.writeTeamSummary("via sys op"));

        assertThat(fileSystem.writes).containsExactly("via sys op");
        assertThat(await(manager.readTeamSummaryText())).isEqualTo("via sys op");
    }

    @Test
    void sysOperationWriteFailureFallsBackToLocalAtomicWrite() throws IOException {
        FakeFileSystem fileSystem = new FakeFileSystem();
        fileSystem.failWrites = true;
        SharedMemoryManager manager = new SharedMemoryManager(tempDir.toString(), fileSystem);

        await(manager.writeTeamSummary("fallback"));

        Path target = tempDir.resolve(SharedMemoryManager.TEAM_MEMORY_FILENAME);
        assertThat(Files.readString(target)).isEqualTo("fallback");
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static final class FakeFileSystem implements TeamMemoryExtractor.FileSystemView {
        private final List<String> writes = new ArrayList<>();
        private String content;
        private boolean failWrites;

        @Override
        public CompletionStage<Optional<String>> readFile(String path) {
            return CompletableFuture.completedFuture(Optional.ofNullable(content));
        }

        @Override
        public CompletionStage<Boolean> writeFile(String path, String content, boolean createIfNotExist) {
            if (failWrites) {
                return CompletableFuture.failedFuture(new IOException("boom"));
            }
            this.content = content;
            writes.add(content);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<List<FileEntry>> listFiles(String path, boolean recursive) {
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
