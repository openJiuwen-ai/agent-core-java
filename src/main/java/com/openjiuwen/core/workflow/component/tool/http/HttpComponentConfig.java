/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import com.openjiuwen.core.workflow.component.ComponentConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * HTTP component configuration.
 * <p>
 * Mirrors Python's {@code HttpComponentConfig}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HttpComponentConfig extends ComponentConfig {

    private HttpRequestParamConfig requestParams;
}