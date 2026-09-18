/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.memory;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;

import java.util.List;
import java.util.Map;

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

    /**
     * Load memory variables for a user/scope.
     *
     * @param userId user identifier
     * @param scopeId scope identifier
     * @return variable name to value
     * @since 0.1.15
     */
    Map<String, String> getVariables(String userId, String scopeId);

    /**
     * Search fragment / user-profile memory and return contents only.
     *
     * @param query search query
     * @param topK max results
     * @param userId user identifier
     * @param scopeId scope identifier
     * @return memory contents
     * @since 0.1.15
     */
    List<String> searchUserMemoryContents(String query, int topK, String userId, String scopeId);

    /**
     * Search summary memory and return contents only.
     *
     * @param query search query
     * @param topK max results
     * @param userId user identifier
     * @param scopeId scope identifier
     * @return summary contents
     * @since 0.1.15
     */
    List<String> searchUserHistorySummaryContents(String query, int topK, String userId, String scopeId);
}
