/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Runtime configuration passed to sandbox operations.
 * <p>
 * All sandbox operations (fs/shell/code) created by the same SysOperation instance
 * share the same SandboxRunConfig object.
 * <p>
 * Mirrors Python's {@code SandboxRunConfig} dataclass from
 * {@code core/sys_operation/sandbox/run_config.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SandboxRunConfig {

    /**
     * Original SandboxGatewayConfig containing scope, sandbox_params, etc.
     */
    private Object config;

    /**
     * Isolation key template with {session_id} placeholder.
     * Use resolveIsolationKey() to get the actual key at invoke time.
     */
    private String isolationKeyTemplate;

    /**
     * Resolve the isolation key by replacing {session_id} placeholder.
     */
    public String resolveIsolationKey(String sessionId) {
        if (isolationKeyTemplate == null) {
            return null;
        }
        return isolationKeyTemplate.replace("{session_id}", sessionId != null ? sessionId : "");
    }
}
