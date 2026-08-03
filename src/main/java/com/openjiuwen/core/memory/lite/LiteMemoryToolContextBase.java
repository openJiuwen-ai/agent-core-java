/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Common workspace-scoped state for MemoryIndexManager-backed tool surfaces.
 *
 * <p>Mirrors Python's {@code LiteMemoryToolContextBase} in
 * {@code openjiuwen/core/memory/lite/memory_tool_context_base.py}.</p>
 */
public class LiteMemoryToolContextBase {

    private Workspace workspace;
    private MemorySettings settings;
    private String agentId = "default";
    private EmbeddingConfig embeddingConfig;
    private Object sysOperation;
    private MemoryIndexManagerView manager;
    private String nodeName = "memory";

    @JsonIgnore
    private transient MemoryIndexManagerProvider managerProvider = new DefaultMemoryIndexManagerProvider();

    public LiteMemoryToolContextBase() {
    }

    public LiteMemoryToolContextBase(
            Workspace workspace,
            MemorySettings settings,
            String agentId,
            EmbeddingConfig embeddingConfig,
            Object sysOperation,
            MemoryIndexManagerView manager,
            String nodeName
    ) {
        this.workspace = workspace;
        this.settings = settings;
        this.agentId = agentId == null ? "default" : agentId;
        this.embeddingConfig = embeddingConfig;
        this.sysOperation = sysOperation;
        this.manager = manager;
        this.nodeName = nodeName == null ? "memory" : nodeName;
    }

    /**
     * Lazy-initialize the memory manager when a workspace is available.
     *
     * @return future resolving to true when a usable manager is present
     */
    public CompletionStage<Boolean> ensureManager() {
        if (manager != null && !manager.isClosed()) {
            return CompletableFuture.completedFuture(true);
        }
        if (workspace == null) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            if (settings == null) {
                settings = new MemorySettings();
            }
            MemoryManagerParams params = new MemoryManagerParams(
                    agentId,
                    workspace,
                    settings,
                    embeddingConfig,
                    sysOperation,
                    nodeName
            );
            return managerProvider.get(params)
                    .thenApply(resolvedManager -> {
                        manager = resolvedManager;
                        return resolvedManager != null;
                    })
                    .exceptionally(throwable -> {
                        Loggers.MEMORY.error(
                                "Failed to initialize memory manager (node_name={}): {}",
                                nodeName,
                                throwable.getMessage(),
                                throwable
                        );
                        return false;
                    });
        } catch (Exception ex) {
            Loggers.MEMORY.error(
                    "Failed to initialize memory manager (node_name={}): {}",
                    nodeName,
                    ex.getMessage(),
                    ex
            );
            return CompletableFuture.completedFuture(false);
        }
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public MemorySettings getSettings() {
        return settings;
    }

    public void setSettings(MemorySettings settings) {
        this.settings = settings;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId == null ? "default" : agentId;
    }

    public EmbeddingConfig getEmbeddingConfig() {
        return embeddingConfig;
    }

    public void setEmbeddingConfig(EmbeddingConfig embeddingConfig) {
        this.embeddingConfig = embeddingConfig;
    }

    public Object getSysOperation() {
        return sysOperation;
    }

    public void setSysOperation(Object sysOperation) {
        this.sysOperation = sysOperation;
    }

    public MemoryIndexManagerView getManager() {
        return manager;
    }

    public void setManager(MemoryIndexManagerView manager) {
        this.manager = manager;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName == null ? "memory" : nodeName;
    }

    public MemoryIndexManagerProvider getManagerProvider() {
        return managerProvider;
    }

    public void setManagerProvider(MemoryIndexManagerProvider managerProvider) {
        this.managerProvider = Objects.requireNonNull(managerProvider, "managerProvider");
    }

    /**
     * Parameters passed to the memory manager provider.
     *
     * <p>Mirrors Python's {@code MemoryManagerParams} usage in
     * {@code openjiuwen/core/memory/lite/memory_tool_context_base.py}.</p>
     */
    public record MemoryManagerParams(
            String agentId,
            Workspace workspace,
            MemorySettings settings,
            EmbeddingConfig embeddingConfig,
            Object sysOperation,
            String nodeName
    ) {
    }

    /**
     * Minimal close-state view of a memory index manager.
     *
     * <p>Mirrors Python's {@code MemoryIndexManager.closed} check in
     * {@code openjiuwen/core/memory/lite/memory_tool_context_base.py}.</p>
     */
    public interface MemoryIndexManagerView {
        boolean isClosed();
    }

    /**
     * Provider matching Python's {@code MemoryIndexManager.get(params)} call.
     *
     * <p>Mirrors Python's lazy manager lookup in
     * {@code openjiuwen/core/memory/lite/memory_tool_context_base.py}.</p>
     */
    public interface MemoryIndexManagerProvider {
        CompletionStage<MemoryIndexManagerView> get(MemoryManagerParams params);
    }

    /**
     * Lightweight default manager view used when no concrete manager bridge is injected.
     *
     * <p>Mirrors Python's successfully initialized manager handle in
     * {@code openjiuwen/core/memory/lite/memory_tool_context_base.py}.</p>
     */
    public static final class DefaultMemoryIndexManagerView implements MemoryIndexManagerView {
        private boolean closed;

        @Override
        public boolean isClosed() {
            return closed;
        }

        public void setClosed(boolean closed) {
            this.closed = closed;
        }
    }

    private static final class DefaultMemoryIndexManagerProvider implements MemoryIndexManagerProvider {
        @Override
        public CompletionStage<MemoryIndexManagerView> get(MemoryManagerParams params) {
            com.openjiuwen.core.memory.lite.MemoryManagerParams managerParams =
                    new com.openjiuwen.core.memory.lite.MemoryManagerParams(
                            params.agentId(),
                            params.workspace(),
                            params.settings(),
                            params.embeddingConfig(),
                            params.sysOperation(),
                            params.nodeName()
                    );
            return MemoryIndexManager.get(managerParams).thenApply(manager -> manager);
        }
    }
}
