/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.utils.UrlUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pulsar message queue configuration.
 *
 * <p>Mirrors Python's {@code PulsarConfig} in
 * {@code openjiuwen/core/runner/runner_config.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PulsarConfig {

    private String url;

    @Builder.Default
    @JsonProperty("max_workers")
    private int maxWorkers = 8;

    public PulsarConfig copy() {
        return PulsarConfig.builder()
                .url(url)
                .maxWorkers(maxWorkers)
                .build();
    }

    public String repr() {
        return "PulsarConfig(url=" + pythonRepr(redactedUrl()) + ", max_workers=" + maxWorkers + ")";
    }

    @Override
    public String toString() {
        return "url=" + pythonRepr(redactedUrl()) + " max_workers=" + maxWorkers;
    }

    private String redactedUrl() {
        return url == null ? null : UrlUtils.redactUrlPassword(url);
    }

    private static String pythonRepr(String value) {
        if (value == null) {
            return "None";
        }
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
