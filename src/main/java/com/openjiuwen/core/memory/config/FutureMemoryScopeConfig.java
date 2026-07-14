/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import java.util.concurrent.CompletableFuture;

/**
 * Memory scope config that also exposes legacy future-style accessors.
 *
 * <p>Mirrors Python's direct config return compatibility in
 * {@code openjiuwen/core/memory/config/config.py}.</p>
 */
public class FutureMemoryScopeConfig extends MemoryScopeConfig {

    public FutureMemoryScopeConfig(MemoryScopeConfig source) {
        super(
                source == null ? null : source.getModelCfg(),
                source == null ? null : source.getModelClientCfg(),
                source == null ? null : source.getEmbeddingCfg(),
                source == null ? null : source.getUserProfileDefinition(),
                source == null ? null : source.getSemanticMemoryDefinition(),
                source == null ? null : source.getEpisodicMemoryDefinition()
        );
    }

    public MemoryScopeConfig join() {
        return this;
    }

    public CompletableFuture<MemoryScopeConfig> toCompletableFuture() {
        return CompletableFuture.completedFuture(this);
    }
}
