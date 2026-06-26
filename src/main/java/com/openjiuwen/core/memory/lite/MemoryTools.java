/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Lite memory manager bootstrap used by MemoryRail.
 *
 * <p>Mirrors Python's {@code init_memory_manager_async} in
 * {@code openjiuwen/core/memory/lite/memory_tools.py}.</p>
 */
public final class MemoryTools {

    public static final String NODE_NAME = "memory";

    private MemoryTools() {
    }

    public static CompletableFuture<LiteMemoryToolContextBase.MemoryIndexManagerView> initMemoryManagerAsync(
            Workspace workspace
    ) {
        return initMemoryManagerAsync(workspace, "default", null, null);
    }

    public static CompletableFuture<LiteMemoryToolContextBase.MemoryIndexManagerView> initMemoryManagerAsync(
            Workspace workspace,
            String agentId
    ) {
        return initMemoryManagerAsync(workspace, agentId, null, null);
    }

    public static CompletableFuture<LiteMemoryToolContextBase.MemoryIndexManagerView> initMemoryManagerAsync(
            Workspace workspace,
            String agentId,
            EmbeddingConfig embeddingConfig,
            Object sysOperation
    ) {
        if (!MemorySettings.isMemoryEnabled()) {
            Loggers.MEMORY.info("Memory system is disabled");
            return CompletableFuture.completedFuture(null);
        }

        Objects.requireNonNull(workspace, "workspace");
        Path nodePath = workspace.getNodePath(NODE_NAME);
        String memoryDir = nodePath == null ? "" : nodePath.toString();
        MemorySettings settings = MemorySettings.createMemorySettings(memoryDir, Map.of());

        MemoryToolContext context = new MemoryToolContext();
        context.setWorkspace(workspace);
        context.setSettings(settings);
        context.setAgentId(agentId);
        context.setEmbeddingConfig(embeddingConfig);
        context.setSysOperation(sysOperation);
        context.setNodeName(NODE_NAME);

        return context.ensureManager()
                .thenApply(initialized -> {
                    LiteMemoryToolContextBase.MemoryIndexManagerView manager =
                            Boolean.TRUE.equals(initialized) ? context.getManager() : null;
                    if (manager != null) {
                        Loggers.MEMORY.info("Memory manager initialized for: {}", memoryDir);
                    }
                    return manager;
                })
                .exceptionally(throwable -> {
                    Loggers.MEMORY.error(
                            "Failed to initialize memory manager: {}",
                            rootMessage(throwable),
                            throwable
                    );
                    return null;
                })
                .toCompletableFuture();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
