/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for container isolation and naming granularity.
 * <p>
 * Mirrors Python's {@code SandboxIsolationConfig} in {@code sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxIsolationConfig {

    /** Core identity override. If set, replaces automatic session_id or context_id. */
    private String customId;

    /** Container granularity template: SYSTEM / SESSION / CUSTOM. */
    @Builder.Default
    private ContainerScope containerScope = ContainerScope.SESSION;

    /** Namespace prefix to isolate multiple roles/tasks in the same scope. */
    private String prefix;
}