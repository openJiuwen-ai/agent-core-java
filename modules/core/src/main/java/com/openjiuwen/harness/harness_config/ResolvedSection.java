/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ResolvedSection.
 * 
 * @since 0.1.7
 */
public record ResolvedSection(String name, int priority, Map<String, String> content) {
    public ResolvedSection {
        content = content == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(content));
    }
}
