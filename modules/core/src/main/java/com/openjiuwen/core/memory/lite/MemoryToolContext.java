/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.workspace.Workspace;

/**
 * Runtime context for general lite memory tools.
 * 
 * @since 0.1.7
 */
public class MemoryToolContext extends LiteMemoryToolContextBase {
    /**
     * MemoryToolContext.
     * 
     * @param workspace workspace
     * @param settings settings
     * @param agentId agentId
     * @param embeddingConfig embeddingConfig
     * @param sysOperation sysOperation
     * @param manager manager
     * @since 0.1.7
     */
    public MemoryToolContext(Workspace workspace, MemorySettings settings, String agentId,
            EmbeddingConfig embeddingConfig, SysOperation sysOperation, MemoryIndexManager manager) {
        this.workspace = workspace;
        this.settings = settings;
        this.agentId = agentId;
        this.embeddingConfig = embeddingConfig;
        this.sysOperation = sysOperation;
        this.manager = manager;
        this.nodeName = "memory";
    }
}
