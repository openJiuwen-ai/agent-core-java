/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for resolving or creating a sandbox endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxCreateRequest {
    private String isolationKey;

    private SandboxGatewayConfig config;
}
