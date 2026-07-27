package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryLiteTest {

    @TempDir
    Path tempDir;

    @Test
    void memoryIndexManagerShouldIndexAndSearchGeneralMemory() throws Exception {
        Path root = tempDir.resolve("workspace");
        Files.createDirectories(root.resolve("memory"));
        Files.writeString(root.resolve("memory").resolve("MEMORY.md"), "release checklist and testing notes");
        Files.writeString(root.resolve("memory").resolve("2026-05-09.md"), "today we shipped release pipeline");

        Workspace workspace = new Workspace(root);
        MemoryIndexManager manager = MemoryIndexManager.get(new MemoryManagerParams(
                "agent-a",
                workspace,
                new MemorySettings(),
                null,
                null,
                "memory"
        )).join();

        var results = manager.search("release", Map.of("max_results", 5, "min_score", 0.2)).join();
        assertThat(results).isNotEmpty();
        assertThat(String.valueOf(results.get(0).get("text"))).contains("release");
    }

    @Test
    void memoryToolOpsShouldWriteReadAndSearch() throws Exception {
        Path root = tempDir.resolve("workspace-tools");
        Files.createDirectories(root.resolve("memory"));
        Workspace workspace = new Workspace(root);
        MemoryToolContext ctx = new MemoryToolContext();
        ctx.setWorkspace(workspace);
        ctx.setSettings(new MemorySettings());
        ctx.setAgentId("agent-b");
        ctx.setNodeName("memory");

        var write = MemoryToolOps.writeMemoryWithContext(ctx, "MEMORY.md", "prefers regression tests", false)
                .toCompletableFuture().join();
        var read = MemoryToolOps.readMemoryWithContext(ctx, "MEMORY.md", null, null)
                .toCompletableFuture().join();
        var search = MemoryToolOps.memorySearchWithContext(ctx, "regression", 5, 0.2, null)
                .toCompletableFuture().join();

        assertThat(write.get("success")).isEqualTo(true);
        assertThat(String.valueOf(read.get("content"))).contains("prefers regression tests");
        assertThat(String.valueOf(search)).contains("regression");
    }

    @Test
    void codingMemoryToolOpsShouldWriteEditAndUpdateIndex() throws Exception {
        Path root = tempDir.resolve("workspace-coding");
        Files.createDirectories(root.resolve("coding_memory"));
        Workspace workspace = new Workspace(root);
        CodingMemoryToolContext ctx = new CodingMemoryToolContext(
                root.resolve("coding_memory").toString());
        ctx.setWorkspace(workspace);
        ctx.setAgentId("agent-c");

        String content = """
                ---
                name: build cache
                description: speed up local build
                type: project
                ---

                enable gradle cache
                """;

        var write = CodingMemoryToolOps.codingMemoryWriteWithContext(ctx, "build-cache.md", content).join();
        var edit = CodingMemoryToolOps.codingMemoryEditWithContext(ctx, "build-cache.md", "enable gradle cache", "enable remote gradle cache").join();
        var read = CodingMemoryToolOps.codingMemoryReadWithContext(ctx, "build-cache.md", null, null).join();

        assertThat(write.get("success")).isEqualTo(true);
        assertThat(edit.get("success")).isEqualTo(true);
        assertThat(String.valueOf(read.get("content"))).contains("enable remote gradle cache");
        assertThat(Files.readString(root.resolve("coding_memory").resolve("MEMORY.md"))).contains("build cache");
    }
}
