/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Schema describing a plugin reference in agent configuration.
 * <p>
 * Mirrors Python's {@code PluginSchema} used in application agent configs.
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
    @JsonProperty("plugin_id")
    @JsonAlias("pluginId")
    private String pluginId = "";
}
