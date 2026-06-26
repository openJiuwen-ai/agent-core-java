/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Focused validation for {@link MemoryIndexManager}.
 *
 * <p>Mirrors Python's {@code MemoryIndexManager} in
 * {@code openjiuwen/core/memory/lite/manager.py}.</p>
 */
public final class MemoryIndexManagerTest {

    private MemoryIndexManagerTest() {
    }

    public static void main(String[] args) throws Exception {
        getCachesOpenManagersAndRecreatesClosedManagers();
        searchReadStatusAndVectorHelpersWork();
        liteContextDefaultProviderUsesRealManager();
        System.out.println("PASS MemoryIndexManagerTest");
    }

    private static void getCachesOpenManagersAndRecreatesClosedManagers() throws Exception {
        MemoryIndexManager.clearMemoryManagerCache();
        Workspace workspace = workspaceWithMemoryFile("alpha project note");
        MemoryManagerParams params = new MemoryManagerParams(
                "agent-a",
                workspace,
                new MemorySettings(),
                null,
                null,
                "memory"
        );

        MemoryIndexManager first = MemoryIndexManager.get(params).join();
        MemoryIndexManager second = MemoryIndexManager.get(params).join();

        require(first == second, "cache hit");
        first.close().join();
        MemoryIndexManager third = MemoryIndexManager.get(params).join();
        require(third != first, "closed manager recreated");
        third.close().join();
    }

    private static void searchReadStatusAndVectorHelpersWork() throws Exception {
        MemoryIndexManager.clearMemoryManagerCache();
        Workspace workspace = workspaceWithMemoryFile("alpha project note\nsecond line");
        MemoryIndexManager manager = MemoryIndexManager.get(new MemoryManagerParams(
                "agent-b",
                workspace,
                new MemorySettings(),
                null,
                null,
                "memory"
        )).join();

        List<Map<String, Object>> rows = manager.search("alpha", Map.of("max_results", 5, "min_score", 0.1d)).join();
        require(rows.size() == 1, "search hit");
        require(String.valueOf(rows.get(0).get("snippet")).contains("alpha"), "snippet");

        Map<String, Object> read = manager.readFile("MEMORY.md", 2, 1).join();
        require("second line".equals(read.get("text")), "read line slice");
        require(Boolean.TRUE.equals(manager.status().get("available")), "status available");

        List<Float> vector = List.of(1.0f, 2.5f, -3.25f);
        require(MemoryIndexManager.blobToVector(MemoryIndexManager.vectorToBlob(vector)).equals(vector),
                "vector round trip");
        manager.close().join();
    }

    private static void liteContextDefaultProviderUsesRealManager() throws Exception {
        MemoryIndexManager.clearMemoryManagerCache();
        Workspace workspace = workspaceWithMemoryFile("context provider note");
        LiteMemoryToolContextBase context = new LiteMemoryToolContextBase();
        context.setWorkspace(workspace);

        require(context.ensureManager().toCompletableFuture().join(), "context ensure manager");
        require(context.getManager() instanceof MemoryIndexManager, "real manager assigned");
    }

    private static Workspace workspaceWithMemoryFile(String content) throws Exception {
        Path root = Files.createTempDirectory("memory-manager-test");
        Path memory = root.resolve("memory");
        Files.createDirectories(memory);
        Files.writeString(memory.resolve("MEMORY.md"), content, StandardCharsets.UTF_8);
        return new Workspace(root);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
