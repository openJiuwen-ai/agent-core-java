/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * HTTP advanced options configuration.
 * <p>
 * Mirrors Python's {@code HttpAdvancedOptionsConfig}.
  * Python file: {@code openjiuwen/core/workflow/components/tool/http/http_request_component.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpAdvancedOptionsConfig {

    @Builder.Default
    private boolean followRedirect = true;
    
    @Builder.Default
    private boolean ignoreSslIssues = false;
    
    private String proxy;
    
    @Builder.Default
    private int timeout = 10000;
    
    @Builder.Default
    private boolean disableCompression = false;
    
    @Builder.Default
    private boolean disableFollowTrackRedirect = false;
    
    @Builder.Default
    private int maxBodyLength = 1048576;
    
    @Builder.Default
    private boolean useStream = false;
    
    private Map<String, String> proxyHeader;
}