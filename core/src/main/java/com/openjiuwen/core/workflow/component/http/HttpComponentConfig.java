/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

import com.openjiuwen.core.workflow.component.ComponentConfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for the HTTP request workflow component.
 * <p>
 * Mirrors Python's {@code HttpComponentConfig}.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HttpComponentConfig extends ComponentConfig {
    private HttpRequestParamConfig requestParams = new HttpRequestParamConfig();

    /**
     * HttpComponentConfig.
     * 
     * @since 0.1.7
     */
    public HttpComponentConfig() {
        super();
    }

    /**
     * HttpComponentConfig.
     * 
     * @param requestParams requestParams
     * @since 0.1.7
     */
    public HttpComponentConfig(HttpRequestParamConfig requestParams) {
        super();
        this.requestParams = requestParams;
    }
}
