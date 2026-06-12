/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session-local promoted runtime extension.
 * <p>
 * Mirrors Python's {@code RuntimeExtensionArtifact} in
 * {@code openjiuwen/auto_harness/schema.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuntimeExtensionArtifact {

    @Builder.Default
    @JsonProperty("extension_name")
    private String extensionName = "";

    @Builder.Default
    @JsonProperty("runtime_path")
    private String runtimePath = "";

    @Builder.Default
    @JsonProperty("config_path")
    private String configPath = "";
}
