/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Coding Memory manager bootstrap used by CodingMemoryRail.
 *
 * <p>Mirrors Python's {@code init_memory_manager_async} in
 * {@code openjiuwen/core/memory/lite/coding_memory_tools.py}.</p>
 */
public final class CodingMemoryTools {

    public static final String NODE_NAME = "coding_memory";

    private CodingMemoryTools() {
    }

    public static CompletableFuture<LiteMemoryToolContextBase.MemoryIndexManagerView> initMemoryManagerAsync(
            Workspace workspace
    ) {
        return initMemoryManagerAsync(workspace, "default", null, null, null);
    }

    public static CompletableFuture<LiteMemoryToolContextBase.MemoryIndexManagerView> initMemoryManagerAsync(
            Workspace workspace,
            String agentId
    ) {
        return initMemoryManagerAsync(workspace, agentId, null, null, null);
    }

    public static CompletableFuture<LiteMemoryToolContextBase.MemoryIndexManagerView> initMemoryManagerAsync(
            Workspace workspace,
            String agentId,
            EmbeddingConfig embeddingConfig,
            Object sysOperation
    ) {
        return initMemoryManagerAsync(workspace, agentId, embeddingConfig, sysOperation, null);
    }

    public static CompletableFuture<LiteMemoryToolContextBase.MemoryIndexManagerView> initMemoryManagerAsync(
            Workspace workspace,
            String agentId,
            EmbeddingConfig embeddingConfig,
            Object sysOperation,
            Object llm
    ) {
        Objects.requireNonNull(workspace, "workspace");
        if (!MemorySettings.isMemoryEnabled()) {
            Loggers.MEMORY.info("Memory system is disabled");
            return CompletableFuture.completedFuture(null);
        }

        Path nodePath = workspace.getNodePath(NODE_NAME);
        String codingMemoryDir = nodePath == null ? "" : nodePath.toString();
        MemorySettings settings = MemorySettings.createMemorySettings(codingMemoryDir, Map.of());

        CodingMemoryToolContext context = new CodingMemoryToolContext(codingMemoryDir);
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
                        attachLlm(manager, llm);
                        Loggers.MEMORY.info("initialized Coding Memory manager for: {}", codingMemoryDir);
                    }
                    return manager;
                })
                .exceptionally(throwable -> {
                    Loggers.MEMORY.error(
                            "Failed to initialize Coding Memory manager: {}",
                            rootMessage(throwable),
                            throwable
                    );
                    return null;
                })
                .toCompletableFuture();
    }

    private static void attachLlm(LiteMemoryToolContextBase.MemoryIndexManagerView manager, Object llm) {
        if (manager == null || llm == null) {
            return;
        }
        if (invokeSetter(manager, llm)) {
            return;
        }
        setField(manager, llm);
    }

    private static boolean invokeSetter(LiteMemoryToolContextBase.MemoryIndexManagerView manager, Object llm) {
        try {
            Method method = manager.getClass().getMethod("setLlm", Object.class);
            method.setAccessible(true);
            method.invoke(manager, llm);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void setField(LiteMemoryToolContextBase.MemoryIndexManagerView manager, Object llm) {
        try {
            Field field = manager.getClass().getDeclaredField("llm");
            field.setAccessible(true);
            field.set(manager, llm);
        } catch (ReflectiveOperationException ignored) {
            // Some lightweight manager bridges do not expose an llm slot. Python only uses this
            // field when downstream conflict detection supports it, so a missing slot is safe.
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
