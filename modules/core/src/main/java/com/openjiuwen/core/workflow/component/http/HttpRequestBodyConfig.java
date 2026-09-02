/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * HTTP request body configuration.
 * <p>
 * Mirrors Python's {@code HttpRequestBodyConfig}.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpRequestBodyConfig {
    private HttpContentType contentType = HttpContentType.JSON;
    private Object jsonData;
    private Map<String, Object> formData;
    private Map<String, Object> multipartForm;
    private String binaryData;
    private String textData;
}
