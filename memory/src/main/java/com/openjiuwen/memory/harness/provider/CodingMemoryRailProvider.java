/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.harness.provider;

import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder.HarnessRailProvider;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.memory.CodingMemoryRail;
import com.openjiuwen.spi.memory.HarnessMemoryRailsResolver;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates and serializes the coding Memory harness rail.
 *
 * @since 0.1.15
 */
public final class CodingMemoryRailProvider implements HarnessRailProvider {
    @Override
    public String name() {
        return "coding_memory";
    }

    @Override
    public DeepAgentRail create() {
        return createCodingMemoryRail("coding_memory", null, "cn");
    }

    @Override
    public DeepAgentRail create(Path workspaceRoot, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = RailConfigSupport.config(spec);
        String language = RailConfigSupport.stringValue(config.get("language"), "cn");
        return createCodingMemoryRail(
                resolveCodingMemoryDir(workspaceRoot, config.get("coding_memory_dir")),
                config.get("embedding_config"),
                language
        );
    }

    @Override
    public boolean supports(Object rail) {
        return rail instanceof CodingMemoryRail;
    }

    @Override
    public Map<String, Object> toConfig(Object rail) {
        if (!(rail instanceof CodingMemoryRail memoryRail)) {
            throw new IllegalArgumentException("Expected CodingMemoryRail configuration source");
        }
        Map<String, Object> config = new LinkedHashMap<>();
        if (memoryRail.getCodingMemoryDir() != null && !memoryRail.getCodingMemoryDir().isBlank()) {
            config.put("coding_memory_dir", memoryRail.getCodingMemoryDir());
        }
        return config;
    }

    private static DeepAgentRail createCodingMemoryRail(
            String codingMemoryDir,
            Object embeddingConfig,
            String language
    ) {
        return HarnessMemoryRailsResolver.find()
                .map(rails -> rails.createCodingMemoryRail(codingMemoryDir, embeddingConfig, language))
                .orElseGet(() -> new CodingMemoryRail(codingMemoryDir, embeddingConfig, language));
    }

    private static String resolveCodingMemoryDir(Path workspaceRoot, Object configuredDirValue) {
        String configuredDir = RailConfigSupport.stringValue(configuredDirValue, null);
        if (configuredDir != null && workspaceRoot != null && !Path.of(configuredDir).isAbsolute()) {
            return workspaceRoot.resolve(configuredDir).toString();
        }
        return configuredDir;
    }
}
