/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Playwright runtime agents configuration.
 *
 * <p>Mirrors Python's agents configuration in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.agents}.
 */
public final class PlaywrightAgents {

    private static final Logger LOG = LoggerFactory.getLogger(PlaywrightAgents.class);

    /**
     * Agent configuration.
     */
    @Data
    @Builder
    public static class AgentConfig {
        private String name;
        private String provider;
        private String apiKey;
        private String apiBase;
        private String modelName;
        @Builder.Default
        private int maxIterations = 25;
        @Builder.Default
        private int timeoutSeconds = 120;
        private Map<String, Object> mcpConfig;
        private List<Object> guardrails;
    }

    /**
     * Create default agent configuration.
     */
    public static AgentConfig defaultConfig() {
        return AgentConfig.builder()
                .name("browser_agent")
                .provider("")
                .apiKey("")
                .apiBase("")
                .modelName("")
                .maxIterations(25)
                .timeoutSeconds(120)
                .mcpConfig(new LinkedHashMap<>())
                .guardrails(new ArrayList<>())
                .build();
    }

    /**
     * Build agent config from settings.
     */
    public static AgentConfig fromSettings(RuntimeSettings settings) {
        return AgentConfig.builder()
                .name("browser_agent")
                .provider(settings.provider())
                .apiKey(settings.apiKey())
                .apiBase(settings.apiBase())
                .modelName(settings.modelName())
                .mcpConfig(settings.mcpCfg() != null ? Map.of("mcp", settings.mcpCfg()) : null)
                .guardrails(settings.guardrails() != null ? List.of(settings.guardrails()) : List.of())
                .build();
    }
}