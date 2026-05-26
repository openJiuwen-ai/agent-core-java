/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.core.common.security.UrlUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pulsar message queue configuration.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.runner.runner_config.PulsarConfig}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PulsarConfig {

    private String url;

    @Builder.Default
    private int maxWorkers = 8;

    @Override
    public String toString() {
        String redactedUrl = url != null ? UrlUtils.redactUrlPassword(url) : null;
        return "PulsarConfig(url=" + redactedUrl + ", maxWorkers=" + maxWorkers + ")";
    }

    public String toSimpleString() {
        String redactedUrl = url != null ? UrlUtils.redactUrlPassword(url) : null;
        return "url=" + redactedUrl + " maxWorkers=" + maxWorkers;
    }
}
