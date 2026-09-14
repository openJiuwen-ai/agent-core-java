/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * HTTP response handling configuration.
 * <p>
 * Mirrors Python's {@code HttpResponseHandlingConfig}.
  * Python file: {@code openjiuwen/core/workflow/components/tool/http/http_request_component.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpResponseHandlingConfig {

    @Builder.Default
    private HttpResponseFormat responseFormat = HttpResponseFormat.AUTODETECT;
    
    private List<Integer> responseCodeSuccessCodes;
    private List<Integer> responseCodeFailureCodes;
    
    @Builder.Default
    private String responseMode = "full";
    
    private String responseDataProperty;
    
    @Builder.Default
    private int maxRedirects = 21;
    
    @Builder.Default
    private boolean throwOnHttpError = true;
}