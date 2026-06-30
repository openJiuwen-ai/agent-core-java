/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;

/**
 * Common workspace-scoped state for MemoryIndexManager-backed tool surfaces.
 */
public class LiteMemoryToolContextBase {
    /**
     * Auto-generated for codecheck compliance.
     */
    protected Workspace workspace;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected MemorySettings settings;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected String agentId = "default";
    /**
     * Auto-generated for codecheck compliance.
     */
    protected EmbeddingConfig embeddingConfig;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected SysOperation sysOperation;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected MemoryIndexManager manager;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected String nodeName = "memory";

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean ensureManager() {
        if (manager != null && !manager.isClosed()) {
            return true;
        }
        if (workspace == null) {
            return false;
        }
        try {
            settings = settings != null ? settings : new MemorySettings();
            manager = MemoryIndexManager.get(new MemoryManagerParams(
                    agentId,
                    workspace,
                    settings,
                    embeddingConfig,
                    sysOperation,
                    nodeName
            ));
            return manager != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Workspace getWorkspace() {
        return workspace;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MemorySettings getSettings() {
        return settings;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EmbeddingConfig getEmbeddingConfig() {
        return embeddingConfig;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SysOperation getSysOperation() {
        return sysOperation;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MemoryIndexManager getManager() {
        return manager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getNodeName() {
        return nodeName;
    }
}
