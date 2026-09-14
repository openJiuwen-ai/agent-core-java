/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.harness.provider;

import com.openjiuwen.core.memory.external.MemoryProvider;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder.HarnessRailProvider;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.memory.ExternalMemoryRail;
import com.openjiuwen.memory.external.Mem0MemoryProvider;
import com.openjiuwen.memory.external.OpenJiuwenMemoryProvider;
import com.openjiuwen.memory.external.OpenVikingMemoryProvider;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Creates the external Memory harness rail.
 *
 * @since 0.1.15
 */
public final class ExternalMemoryRailProvider implements HarnessRailProvider {
    @Override
    public String name() {
        return "external_memory";
    }

    @Override
    public DeepAgentRail create() {
        return new ExternalMemoryRail(null);
    }

    @Override
    public DeepAgentRail create(Path workspaceRoot, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = RailConfigSupport.config(spec);
        MemoryProvider provider = createMemoryProvider(config).orElse(null);
        String userId = stringValue(config, "__default__", "user_id", "userId");
        String scopeId = stringValue(config, "__default__", "scope_id", "scopeId");
        String sessionId = stringValue(config, "__default__", "session_id", "sessionId");
        return new ExternalMemoryRail(provider, userId, scopeId, sessionId);
    }

    @Override
    public boolean supports(Object rail) {
        return rail instanceof ExternalMemoryRail;
    }

    private static Optional<MemoryProvider> createMemoryProvider(Map<String, Object> config) {
        String providerName = stringValue(config, "", "provider", "provider_name", "providerName");
        if (providerName.isBlank()) {
            return Optional.empty();
        }
        MemoryProvider provider = switch (providerName.toLowerCase(Locale.ROOT)) {
            case "openjiuwen", "jiuwen", "default" ->
                    new OpenJiuwenMemoryProvider(RailConfigSupport.providerConfig(config));
            case "mem0" -> new Mem0MemoryProvider();
            case "openviking", "viking" -> new OpenVikingMemoryProvider();
            default -> throw new IllegalArgumentException("Unknown external memory provider: " + providerName);
        };
        return Optional.of(provider);
    }

    private static String stringValue(Map<String, Object> config, String defaultValue, String... keys) {
        return RailConfigSupport.stringValue(
                RailConfigSupport.firstPresent(config, keys).orElse(defaultValue),
                defaultValue
        );
    }
}
