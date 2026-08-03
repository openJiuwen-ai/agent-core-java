/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Focused validation for {@link MemoryTools}.
 *
 * <p>Mirrors Python's {@code init_memory_manager_async} in
 * {@code openjiuwen/core/memory/lite/memory_tools.py}.</p>
 */
public final class MemoryToolsTest {

    private MemoryToolsTest() {
    }

    public static void main(String[] args) throws Exception {
        initMemoryManagerAsyncCreatesMemoryManager();
        System.out.println("PASS MemoryToolsTest");
    }

    private static void initMemoryManagerAsyncCreatesMemoryManager() throws Exception {
        if (!MemorySettings.isMemoryEnabled()) {
            return;
        }
        Path root = Files.createTempDirectory("memory-tools-test");
        Workspace workspace = new Workspace(root);

        LiteMemoryToolContextBase.MemoryIndexManagerView manager =
                MemoryTools.initMemoryManagerAsync(workspace, "agent-1", null, null)
                        .get(5, TimeUnit.SECONDS);

        require(manager != null, "manager initialized");
        require(!manager.isClosed(), "manager open");
        require(manager instanceof MemoryIndexManager, "real manager");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
