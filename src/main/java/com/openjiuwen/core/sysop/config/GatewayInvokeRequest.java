/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request model for gateway full-chain routing.
 * <p>
 * Mirrors Python's {@code GatewayInvokeRequest} in
 * {@code openjiuwen/core/sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayInvokeRequest {

    @JsonProperty("op_type")
    private String opType;

    private String method;

    @Builder.Default
    private Map<String, Object> params = new LinkedHashMap<>();

    @JsonProperty("isolation_key")
    private String isolationKey;
}
