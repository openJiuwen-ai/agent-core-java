/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.harness.provider;

import com.openjiuwen.core.memory.external.MemoryProvider;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder.HarnessRailProvider;
import com.openjiuwen.memory.harness.rails.ExternalMemoryRail;
import com.openjiuwen.memory.provider.local.OpenJiuwenMemoryProvider;
import com.openjiuwen.memory.provider.remote.Mem0MemoryProvider;
import com.openjiuwen.memory.provider.remote.OpenVikingMemoryProvider;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Creates the external Memory harness rail.
 *
 * @since 0.1.7
 */
public final class ExternalMemoryRailProvider implements HarnessRailProvider {
    @Override
    public String name() {
        return "external_memory";
    }

    @Override
    public Object create() {
        return new ExternalMemoryRail();
    }

    @Override
    public Object create(Path workspaceRoot, HarnessConfig.RailResourceSchema spec) {
        Map<String, Object> config = RailConfigSupport.config(spec);
        MemoryProvider provider = createMemoryProvider(config);
        String userId = stringValue(config, "__default__", "user_id", "userId");
        String scopeId = stringValue(config, "__default__", "scope_id", "scopeId");
        String sessionId = stringValue(config, "__default__", "session_id", "sessionId");
        return new ExternalMemoryRail(provider, userId, scopeId, sessionId);
    }

    @Override
    public boolean supports(Object rail) {
        return rail instanceof ExternalMemoryRail;
    }

    private static MemoryProvider createMemoryProvider(Map<String, Object> config) {
        String providerName = stringValue(config, "", "provider", "provider_name", "providerName");
        if (providerName.isBlank()) {
            return null;
        }
        return switch (providerName.toLowerCase(Locale.ROOT)) {
            case "openjiuwen", "jiuwen", "default" ->
                new OpenJiuwenMemoryProvider(RailConfigSupport.providerConfig(config), null, null);
            case "mem0" -> new Mem0MemoryProvider();
            case "openviking", "viking" -> new OpenVikingMemoryProvider();
            default -> throw new IllegalArgumentException("Unknown external memory provider: " + providerName);
        };
    }

    private static String stringValue(Map<String, Object> config, String defaultValue, String... keys) {
        return RailConfigSupport.stringValue(RailConfigSupport.firstPresent(config, keys), defaultValue);
    }
}
