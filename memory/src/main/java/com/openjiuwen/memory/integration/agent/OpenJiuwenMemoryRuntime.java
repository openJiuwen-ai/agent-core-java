/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.integration.agent;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.memory.LongTermMemory;
import com.openjiuwen.memory.MemResult;
import com.openjiuwen.memory.config.MemoryScopeConfig;
import com.openjiuwen.spi.memory.MemoryRuntime;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default Core agent integration backed by {@link LongTermMemory}.
 *
 * @since 0.1.15
 */
public final class OpenJiuwenMemoryRuntime implements MemoryRuntime {
    @Override
    public boolean configureScope(String scopeId) {
        return Boolean.TRUE.equals(LongTermMemory.getInstance()
                .setScopeConfig(scopeId, new MemoryScopeConfig())
                .join());
    }

    @Override
    public void addMessages(List<BaseMessage> messages, AgentMemoryConfig config, String userId, String scopeId,
            String sessionId) {
        LongTermMemory.getInstance().addMessages(
                messages,
                config,
                userId,
                scopeId,
                sessionId,
                ZonedDateTime.now(),
                true,
                2
        ).join();
    }

    @Override
    public Map<String, String> getVariables(String userId, String scopeId) {
        Map<String, String> variables = LongTermMemory.getInstance()
                .getVariables(null, userId, scopeId)
                .toCompletableFuture()
                .join();
        return variables == null ? Map.of() : variables;
    }

    @Override
    public List<String> searchUserMemoryContents(String query, int topK, String userId, String scopeId) {
        return contents(LongTermMemory.getInstance()
                .searchUserMem(query, topK, userId, scopeId, 0.0)
                .toCompletableFuture()
                .join());
    }

    @Override
    public List<String> searchUserHistorySummaryContents(String query, int topK, String userId, String scopeId) {
        return contents(LongTermMemory.getInstance()
                .searchUserHistorySummary(query, topK, userId, scopeId, 0.0)
                .toCompletableFuture()
                .join());
    }

    private static List<String> contents(List<MemResult> mems) {
        List<String> result = new ArrayList<>();
        if (mems == null) {
            return result;
        }
        for (MemResult mem : mems) {
            if (mem != null && mem.getMemInfo() != null && mem.getMemInfo().getContent() != null) {
                result.add(mem.getMemInfo().getContent());
            }
        }
        return result;
    }
}
