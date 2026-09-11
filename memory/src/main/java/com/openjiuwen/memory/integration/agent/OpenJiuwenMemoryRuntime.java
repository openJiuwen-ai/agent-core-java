/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.integration.agent;

import com.openjiuwen.core.application.schema.AgentMemoryConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.memory.LongTermMemory;
import com.openjiuwen.memory.config.MemoryScopeConfig;
import com.openjiuwen.spi.memory.MemoryRuntime;

import java.util.List;

/**
 * Default Core agent integration backed by {@link LongTermMemory}.
 *
 * @since 0.1.15
 */
public final class OpenJiuwenMemoryRuntime implements MemoryRuntime {
    @Override
    public boolean configureScope(String scopeId) {
        return LongTermMemory.getInstance().setScopeConfig(scopeId, new MemoryScopeConfig());
    }

    @Override
    public void addMessages(List<BaseMessage> messages, AgentMemoryConfig config, String userId, String scopeId,
            String sessionId) {
        LongTermMemory.getInstance().addMessages(messages, config, userId, scopeId, sessionId);
    }
}
