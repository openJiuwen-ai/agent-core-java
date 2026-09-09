/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.harness.provider;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder.HarnessRailProvider;
import com.openjiuwen.memory.harness.rails.MemoryRail;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates and serializes the standard Memory harness rail.
 *
 * @since 0.1.7
 */
public final class MemoryRailProvider implements HarnessRailProvider {
    @Override
    public String name() {
        return "memory";
    }

    @Override
    public Object create() {
        return new MemoryRail();
    }

    @Override
    public Object create(Path workspaceRoot, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = RailConfigSupport.config(spec);
        EmbeddingConfig embeddingConfig = config.get("embedding_config") instanceof EmbeddingConfig embedding
                ? embedding
                : null;
        return new MemoryRail(embeddingConfig, RailConfigSupport.booleanValue(config.get("isProactive"), true));
    }

    @Override
    public boolean supports(Object rail) {
        return rail != null && rail.getClass() == MemoryRail.class;
    }

    @Override
    public Map<String, Object> toConfig(Object rail) {
        MemoryRail memoryRail = (MemoryRail) rail;
        Map<String, Object> config = new LinkedHashMap<>();
        if (!memoryRail.isProactive()) {
            config.put("isProactive", false);
        }
        return config;
    }
}
