/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Request model for Gateway full-chain routing.
 * <p>
 * Contains operation type, method name, parameters, and isolation key.
 * <p>
 * Mirrors Python's {@code GatewayInvokeRequest} in {@code sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayInvokeRequest {

    /** Operation type: fs / shell / code. */
    private String opType;

    /** Method name (e.g., read_file, execute_cmd). */
    private String method;

    /** Method parameters. */
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /** Sandbox isolation key. */
    private String isolationKey;
}