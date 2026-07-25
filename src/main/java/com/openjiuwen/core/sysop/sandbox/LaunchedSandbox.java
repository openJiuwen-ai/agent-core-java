/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Descriptor returned by {@link SandboxLauncher#launch}.
 *
 * <p>Mirrors Python's {@code LaunchedSandbox} in
 * {@code sandbox/launchers/base.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaunchedSandbox {
    private String baseUrl;

    private String sandboxId;

    private Integer hostPort;
}
