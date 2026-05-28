/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for creating a sandbox.
 * <p>
 * Mirrors Python's {@code SandboxCreateRequest} in {@code sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxCreateRequest {

    /** Isolation key for sandbox identification. */
    private String isolationKey;

    /** Sandbox gateway configuration. */
    private SandboxGatewayConfig config;
}