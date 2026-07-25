/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import java.nio.file.Path;

/**
 * Public interface HarnessConfigProvider used by the Java parity implementation.
 *
 * @since 1.0
 */
public interface HarnessConfigProvider {
    HarnessConfigInfo describe();

    Path getConfigPath();
}
