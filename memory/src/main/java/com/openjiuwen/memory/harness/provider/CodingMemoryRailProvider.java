/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.harness.provider;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder.HarnessRailProvider;
import com.openjiuwen.memory.harness.rails.CodingMemoryRail;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates and serializes the coding Memory harness rail.
 *
 * @since 0.1.7
 */
public final class CodingMemoryRailProvider implements HarnessRailProvider {
    @Override
    public String name() {
        return "coding_memory";
    }

    @Override
    public Object create() {
        return new CodingMemoryRail();
    }

    @Override
    public Object create(Path workspaceRoot, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = RailConfigSupport.config(spec);
        String configuredDir = RailConfigSupport.stringValue(config.get("coding_memory_dir"), null);
        if (configuredDir != null && !Path.of(configuredDir).isAbsolute()) {
            configuredDir = workspaceRoot.resolve(configuredDir).toString();
        }
        EmbeddingConfig embeddingConfig = config.get("embedding_config") instanceof EmbeddingConfig embedding
                ? embedding
                : null;
        boolean isProactive = RailConfigSupport.booleanValue(config.get("isProactive"), true);
        return new CodingMemoryRail(configuredDir, embeddingConfig, isProactive);
    }

    @Override
    public boolean supports(Object rail) {
        return rail instanceof CodingMemoryRail;
    }

    @Override
    public Map<String, Object> toConfig(Object rail) {
        CodingMemoryRail memoryRail = (CodingMemoryRail) rail;
        Map<String, Object> config = new LinkedHashMap<>();
        if (memoryRail.codingMemoryDir() != null && !memoryRail.codingMemoryDir().isBlank()) {
            config.put("coding_memory_dir", memoryRail.codingMemoryDir());
        }
        if (!memoryRail.isProactive()) {
            config.put("isProactive", false);
        }
        return config;
    }
}
