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
 * 
 * @since 0.1.7
 */
public class LiteMemoryToolContextBase {
    /**
     * workspace.
     * 
     * @since 0.1.7
     */
    protected Workspace workspace;

    /**
     * settings.
     * 
     * @since 0.1.7
     */
    protected MemorySettings settings;

    /**
     * agentId.
     * 
     * @since 0.1.7
     */
    protected String agentId = "default";

    /**
     * embeddingConfig.
     * 
     * @since 0.1.7
     */
    protected EmbeddingConfig embeddingConfig;

    /**
     * sysOperation.
     * 
     * @since 0.1.7
     */
    protected SysOperation sysOperation;

    /**
     * manager.
     * 
     * @since 0.1.7
     */
    protected MemoryIndexManager manager;

    /**
     * nodeName.
     * 
     * @since 0.1.7
     */
    protected String nodeName = "memory";

    /**
     * ensureManager.
     * 
     * @return the result
     * @since 0.1.7
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
            manager = MemoryIndexManager.get(
                    new MemoryManagerParams(agentId, workspace, settings, embeddingConfig, sysOperation, nodeName));
            return manager != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * getWorkspace.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Workspace getWorkspace() {
        return workspace;
    }

    /**
     * getSettings.
     * 
     * @return the result
     * @since 0.1.7
     */
    public MemorySettings getSettings() {
        return settings;
    }

    /**
     * getAgentId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * getEmbeddingConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public EmbeddingConfig getEmbeddingConfig() {
        return embeddingConfig;
    }

    /**
     * getSysOperation.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SysOperation getSysOperation() {
        return sysOperation;
    }

    /**
     * getManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public MemoryIndexManager getManager() {
        return manager;
    }

    /**
     * getNodeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getNodeName() {
        return nodeName;
    }
}
