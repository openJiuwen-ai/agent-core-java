/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Legacy plugin schema for backward compatibility.
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
    private Map<String, Object> inputs = new LinkedHashMap<>();

    @Builder.Default
    private String pluginId = "";
}
