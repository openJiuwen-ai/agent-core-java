/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Isolation and naming strategy for sandbox instances.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxIsolationConfig {
    private String customId;

    @Builder.Default
    private ContainerScope containerScope = ContainerScope.SESSION;

    private String prefix;
}
