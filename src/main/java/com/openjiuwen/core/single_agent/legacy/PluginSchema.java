/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.legacy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Legacy plugin schema for backward compatibility.
 *
 * <p>Mirrors Python's {@code PluginSchema} in
 * {@code openjiuwen.core.single_agent.legacy.schema}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginSchema {

    @Builder.Default
    private String id = "";

    @Builder.Default
    private String version = "";

    @Builder.Default
    private String name = "";

    @Builder.Default
    private String description = "";

    @Builder.Default
    private Map<String, Object> inputs = new HashMap<>();

    @Builder.Default
    private String pluginId = "";
}