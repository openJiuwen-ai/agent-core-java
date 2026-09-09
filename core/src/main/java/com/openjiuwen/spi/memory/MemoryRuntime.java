/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.memory;

import com.openjiuwen.core.application.schema.AgentMemoryConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.List;

/**
 * Host contract for the Memory implementation used by Core agents.
 *
 * @since 0.1.15
 */
public interface MemoryRuntime {
    /**
     * Configure a Memory scope with its default scope configuration.
     *
     * @param scopeId scope identifier
     * @return whether the scope configuration was applied
     * @since 0.1.15
     */
    boolean configureScope(String scopeId);

    /**
     * Add a batch of agent messages to Memory.
     *
     * @param messages messages to persist
     * @param config agent memory configuration
     * @param userId user identifier
     * @param scopeId scope identifier
     * @param sessionId session identifier
     * @since 0.1.15
     */
    void addMessages(List<BaseMessage> messages, AgentMemoryConfig config, String userId, String scopeId,
            String sessionId);
}
