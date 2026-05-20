/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import java.util.LinkedHashMap;
import java.util.Map;

public record ResolvedSection(String name, int priority, Map<String, String> content) {
    /**
     * Auto-generated for codecheck compliance.
     */
    public ResolvedSection {
        content = content == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(content));
    }
}
