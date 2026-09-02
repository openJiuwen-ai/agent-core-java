/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.workspace.Workspace;

/**
 * Runtime context for coding lite memory tools.
 * 
 * @since 0.1.7
 */
public class CodingMemoryToolContext extends LiteMemoryToolContextBase {
    private final String codingMemoryDir;

    /**
     * CodingMemoryToolContext.
     * 
     * @param workspace workspace
     * @param settings settings
     * @param agentId agentId
     * @param embeddingConfig embeddingConfig
     * @param sysOperation sysOperation
     * @param manager manager
     * @param codingMemoryDir codingMemoryDir
     * @since 0.1.7
     */
    public CodingMemoryToolContext(Workspace workspace, MemorySettings settings, String agentId,
            EmbeddingConfig embeddingConfig, SysOperation sysOperation, MemoryIndexManager manager,
            String codingMemoryDir) {
        this.workspace = workspace;
        this.settings = settings;
        this.agentId = agentId;
        this.embeddingConfig = embeddingConfig;
        this.sysOperation = sysOperation;
        this.manager = manager;
        this.nodeName = "coding_memory";
        this.codingMemoryDir = codingMemoryDir;
    }

    /**
     * getCodingMemoryDir.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getCodingMemoryDir() {
        return codingMemoryDir;
    }
}
