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
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HttpComponentConfig extends ComponentConfig {
    private HttpRequestParamConfig requestParams = new HttpRequestParamConfig();

    public HttpComponentConfig() {
        super();
    }

    public HttpComponentConfig(HttpRequestParamConfig requestParams) {
        super();
        this.requestParams = requestParams;
    }
}
