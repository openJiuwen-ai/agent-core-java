/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gateway response wrapper.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayResponse {
    private int code;

    private String message;

    private Object data;

    /**
     * isSuccess.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isSuccess() {
        return code == 0;
    }
}
