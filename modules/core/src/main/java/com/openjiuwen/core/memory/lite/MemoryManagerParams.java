/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.workspace.Workspace;

/**
 * Parameters for creating or retrieving a MemoryIndexManager instance.
 * 
 * @since 0.1.7
 */
public record MemoryManagerParams(String agentId, Workspace workspace, MemorySettings settings,
        EmbeddingConfig embeddingConfig, SysOperation sysOperation, String nodeName) {
}
