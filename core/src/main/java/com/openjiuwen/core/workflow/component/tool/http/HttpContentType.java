/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP content type enum.
 * <p>
 * Mirrors Python's {@code HttpContentType}.
  * Python file: {@code openjiuwen/core/workflow/components/tool/http/http_request_component.py}.
 */
public enum HttpContentType {
    JSON,
    FORM,
    MULTIPART_FORM,
    BINARY,
    TEXT,
    AUTO
}