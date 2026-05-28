/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP response format enum.
 * <p>
 * Mirrors Python's {@code HttpResponseFormat}.
 */
public enum HttpResponseFormat {
    AUTODETECT,
    JSON,
    TEXT,
    BINARY,
    BUFFER
}