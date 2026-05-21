/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP authentication type enum.
 * <p>
 * Mirrors Python's {@code HttpAuthType}.
 */
public enum HttpAuthType {
    NONE,
    BASIC,
    BEARER,
    API_KEY,
    DIGEST,
    AWS
}