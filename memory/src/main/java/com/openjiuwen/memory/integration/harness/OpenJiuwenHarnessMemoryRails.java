/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.integration.harness;

import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.memory.CodingMemoryRail;
import com.openjiuwen.harness.rails.memory.MemoryRail;
import com.openjiuwen.spi.memory.HarnessMemoryRails;

import java.util.Map;

/**
 * Default Harness memory rails backed by the Memory module.
 *
 * @since 0.1.15
 */
public final class OpenJiuwenHarnessMemoryRails implements HarnessMemoryRails {
    @Override
    public DeepAgentRail createMemoryRail(Map<String, Object> embeddingConfig) {
        return new MemoryRail(embeddingConfig);
    }

    @Override
    public DeepAgentRail createCodingMemoryRail(String codingMemoryDir, Object embeddingConfig, String language) {
        return new CodingMemoryRail(codingMemoryDir, embeddingConfig, language);
    }
}
