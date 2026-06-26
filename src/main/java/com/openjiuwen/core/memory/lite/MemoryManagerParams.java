/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.harness.workspace.Workspace;

/**
 * Parameters for creating or retrieving a MemoryIndexManager instance.
 *
 * <p>Mirrors Python's {@code MemoryManagerParams} in
 * {@code openjiuwen/core/memory/lite/manager.py}.</p>
 */
public class MemoryManagerParams {

    private String agentId = "default";
    private Workspace workspace;
    private MemorySettings settings;
    private EmbeddingConfig embeddingConfig;
    private Object sysOperation;
    private String nodeName = "memory";

    public MemoryManagerParams() {
    }

    public MemoryManagerParams(
            String agentId,
            Workspace workspace,
            MemorySettings settings,
            EmbeddingConfig embeddingConfig,
            Object sysOperation,
            String nodeName
    ) {
        this.agentId = agentId == null ? "default" : agentId;
        this.workspace = workspace;
        this.settings = settings;
        this.embeddingConfig = embeddingConfig;
        this.sysOperation = sysOperation;
        this.nodeName = nodeName == null ? "memory" : nodeName;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId == null ? "default" : agentId;
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

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName == null ? "memory" : nodeName;
    }
}
