/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import java.nio.file.Path;

/**
 * Public interface HarnessConfigProvider used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public interface HarnessConfigProvider {
    /**
     * describe.
     * 
     * @return the result
     * @since 0.1.7
     */
    HarnessConfigInfo describe();

    /**
     * getConfigPath.
     * 
     * @return the result
     * @since 0.1.7
     */
    Path getConfigPath();
}
