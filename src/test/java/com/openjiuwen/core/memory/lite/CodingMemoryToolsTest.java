/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Focused validation for {@link CodingMemoryTools}.
 *
 * <p>Mirrors Python's {@code init_memory_manager_async} in
 * {@code openjiuwen/core/memory/lite/coding_memory_tools.py}.</p>
 */
public final class CodingMemoryToolsTest {

    private CodingMemoryToolsTest() {
    }

    public static void main(String[] args) throws Exception {
        initMemoryManagerAsyncCreatesCodingMemoryManager();
        System.out.println("PASS CodingMemoryToolsTest");
    }

    private static void initMemoryManagerAsyncCreatesCodingMemoryManager() throws Exception {
        if (!MemorySettings.isMemoryEnabled()) {
            return;
        }
        Path root = Files.createTempDirectory("coding-memory-tools-test");
        Workspace workspace = new Workspace(root);
        Object llm = new Object();

        LiteMemoryToolContextBase.MemoryIndexManagerView manager =
                CodingMemoryTools.initMemoryManagerAsync(workspace, "agent-1", null, null, llm)
                        .get(5, TimeUnit.SECONDS);

        require(manager != null, "manager initialized");
        require(!manager.isClosed(), "manager open");
        require(manager instanceof MemoryIndexManager, "real manager");
        require(((MemoryIndexManager) manager).getLlm() == llm, "llm attached");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
