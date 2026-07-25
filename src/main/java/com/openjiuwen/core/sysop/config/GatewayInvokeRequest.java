/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request model for full-chain gateway invoke routing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayInvokeRequest {
    private String opType;

    private String method;

    @Builder.Default
    private Map<String, Object> params = new LinkedHashMap<>();

    private String isolationKey;
}
