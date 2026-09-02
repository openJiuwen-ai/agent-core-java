/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Legacy memory configuration.
 * <p>
 * Mirrors Python's {@code MemoryConfig} in {@code single_agent/legacy/config.py}.
 * </p>
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryConfig {
    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private String scope = "";

    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> config = new LinkedHashMap<>();
}
