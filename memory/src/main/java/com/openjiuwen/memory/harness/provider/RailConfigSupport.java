/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.harness.provider;

import com.openjiuwen.harness.harness_config.HarnessConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Shared configuration conversion helpers for Memory harness rail providers.
 *
 * @since 0.1.7
 */
final class RailConfigSupport {
    private RailConfigSupport() {
    }

    static Map<String, Object> config(HarnessConfig.RailResourceSchema spec) {
        if (spec == null || spec.getConfig() == null) {
            return Map.of();
        }
        return spec.getConfig();
    }

    static boolean booleanValue(Object value, boolean isDefaultValue) {
        return value instanceof Boolean isBooleanValue ? isBooleanValue : isDefaultValue;
    }

    static String stringValue(Object value, String defaultValue) {
        return value instanceof String str ? str : defaultValue;
    }

    static Optional<Object> firstPresent(Map<String, Object> config, String... keys) {
        for (String key : keys) {
            if (config.containsKey(key)) {
                return Optional.ofNullable(config.get(key));
            }
        }
        return Optional.empty();
    }

    static Map<String, Object> providerConfig(Map<String, Object> config) {
        Optional<Object> nested = firstPresent(config, "provider_config", "providerConfig", "config");
        if (nested.isPresent() && nested.get() instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>(config);
        result.remove("provider");
        result.remove("provider_name");
        result.remove("providerName");
        return result;
    }
}
