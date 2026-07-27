/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * HTTP advanced options configuration.
 * <p>
 * Mirrors Python's {@code HttpAdvancedOptionsConfig}.
 *
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpAdvancedOptionsConfig {
    private boolean isFollowRedirect = true;
    private boolean isIgnoreSslIssues = false;
    private String proxy;
    private int timeout = 10000; // milliseconds
    private boolean isDisableCompression = false;
    private boolean isDisableFollowTrackRedirect = false;
    private int maxBodyLength = 1048576; // 1MB
    private boolean isUseStream = false;
    private Map<String, String> proxyHeader;
}
