/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.memory;

import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.Map;

/**
 * Optional Harness memory rails provided by the Memory module.
 *
 * @since 0.1.15
 */
public interface HarnessMemoryRails {
    /**
     * Create the default memory rail.
     *
     * @param embeddingConfig embedding configuration map
     * @return a DeepAgent rail
     * @since 0.1.15
     */
    DeepAgentRail createMemoryRail(Map<String, Object> embeddingConfig);

    /**
     * Create the coding-memory rail.
     *
     * @param codingMemoryDir memory directory
     * @param embeddingConfig embedding configuration
     * @param language language hint
     * @return a DeepAgent rail
     * @since 0.1.15
     */
    DeepAgentRail createCodingMemoryRail(String codingMemoryDir, Object embeddingConfig, String language);
}
