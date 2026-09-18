/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.harness.provider;

import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder.HarnessRailProvider;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.memory.MemoryRail;
import com.openjiuwen.spi.memory.HarnessMemoryRails;
import com.openjiuwen.spi.memory.HarnessMemoryRailsResolver;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Creates and serializes the standard Memory harness rail.
 *
 * @since 0.1.15
 */
public final class MemoryRailProvider implements HarnessRailProvider {
    @Override
    public String name() {
        return "memory";
    }

    @Override
    public DeepAgentRail create() {
        return createMemoryRail(null, true);
    }

    @Override
    public DeepAgentRail create(Path workspaceRoot, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = RailConfigSupport.config(spec);
        return createMemoryRail(
                config.get("embedding_config"),
                RailConfigSupport.booleanValue(config.get("isProactive"), true)
        );
    }

    @Override
    public boolean supports(Object rail) {
        return rail != null && rail.getClass() == MemoryRail.class;
    }

    @Override
    public Map<String, Object> toConfig(Object rail) {
        if (!(rail instanceof MemoryRail memoryRail)) {
            throw new IllegalArgumentException("Expected MemoryRail configuration source");
        }
        Map<String, Object> config = new LinkedHashMap<>();
        if (!memoryRail.isProactive()) {
            config.put("isProactive", false);
        }
        return config;
    }

    private static DeepAgentRail createMemoryRail(Object embeddingConfig, boolean isProactive) {
        Optional<HarnessMemoryRails> rails = HarnessMemoryRailsResolver.find();
        if (rails.isEmpty() || !isProactive || !isMapOrNull(embeddingConfig)) {
            return new MemoryRail(embeddingConfig, isProactive);
        }
        return rails.get().createMemoryRail(RailConfigSupport.optionalMap(embeddingConfig).orElse(null));
    }

    private static boolean isMapOrNull(Object embeddingConfig) {
        return embeddingConfig == null || embeddingConfig instanceof Map<?, ?>;
    }
}
