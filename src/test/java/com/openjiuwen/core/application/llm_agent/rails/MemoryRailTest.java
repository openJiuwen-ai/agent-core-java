/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent.rails;

import com.openjiuwen.core.common.async.FutureList;
import com.openjiuwen.core.common.async.FutureMap;
import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.AddMemResult;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemInfo;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for memory lifecycle rail behavior.
 *
 * <p>Mirrors Python's {@code MemoryRail} in
 * {@code openjiuwen/core/application/llm_agent/rails/memory_rail.py}.</p>
 */
class MemoryRailTest {

    @Test
    void beforeInvokeSkipsResumePath() {
        RecordingMemory memory = new RecordingMemory();
        AgentCallbackContext context = contextWithQuery("hello", "u1");
        context.getExtra().put("is_resume", true);

        rail(memory).beforeInvoke(context).toCompletableFuture().join();

        assertFalse(context.getExtra().containsKey("memory_variables"));
        assertFalse(memory.getVariablesCalled);
    }

    @Test
    void beforeInvokeLoadsVariablesAndLongTermMemory() {
        RecordingMemory memory = new RecordingMemory();
        memory.variables.put("profile", "coder");
        memory.variables.put("ignored", "secret");
        memory.userMems = List.of(mem("likes java"));
        memory.summaryMems = List.of(mem("summary one"));
        AgentCallbackContext context = contextWithQuery("hello", "u1");

        rail(memory).beforeInvoke(context).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        Map<String, Object> memoryVariables = (Map<String, Object>) context.getExtra().get("memory_variables");
        assertEquals("{\"profile\":\"coder\"}", memoryVariables.get("sys_memory_variables"));
        assertTrue(String.valueOf(memoryVariables.get("sys_long_term_memory")).contains("用户画像记忆："));
        assertTrue(String.valueOf(memoryVariables.get("sys_long_term_memory")).contains("likes java"));
        assertTrue(String.valueOf(memoryVariables.get("sys_long_term_memory")).contains("摘要记忆："));
        assertEquals("hello", context.getExtra().get("_original_query"));
        assertEquals("hello", memory.searchUserMemQuery);
        assertEquals("u1", memory.searchUserMemUserId);
        assertEquals("scope-a", memory.searchUserMemScopeId);
        assertEquals(10, memory.searchUserMemNum);
        assertEquals(5, memory.searchUserHistorySummaryNum);
    }

    @Test
    void beforeInvokeFallsBackToEmptyLongTermMemoryOnSearchFailure() {
        RecordingMemory memory = new RecordingMemory();
        memory.searchError = new IllegalStateException("search failed");
        AgentCallbackContext context = contextWithQuery("hello", "u1");

        rail(memory).beforeInvoke(context).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        Map<String, Object> memoryVariables = (Map<String, Object>) context.getExtra().get("memory_variables");
        assertEquals("[]", memoryVariables.get("sys_long_term_memory"));
    }

    @Test
    void afterInvokeWritesAnswerConversation() {
        RecordingMemory memory = new RecordingMemory();
        AgentCallbackContext context = contextWithQuery("hello", "u1");
        context.getExtra().put("_original_query", "hello");
        InvokeInputs inputs = (InvokeInputs) context.getInputs();
        inputs.setConversationId("conv-1");
        inputs.setResult(new LinkedHashMap<>(Map.of(
                "result_type", "answer",
                "output", "done"
        )));

        rail(memory).afterInvoke(context).toCompletableFuture().join();

        assertTrue(memory.addMessagesCalled);
        assertEquals("u1", memory.addUserId);
        assertEquals("scope-a", memory.addScopeId);
        assertEquals("conv-1", memory.addSessionId);
        assertEquals("user", memory.addedMessages.get(0).getRole());
        assertEquals("hello", memory.addedMessages.get(0).getContentAsString());
        assertEquals("assistant", memory.addedMessages.get(1).getRole());
        assertEquals("done", memory.addedMessages.get(1).getContentAsString());
        assertNotNull(memory.addTimestamp);
    }

    @Test
    void afterInvokeSkipsNonAnswerResult() {
        RecordingMemory memory = new RecordingMemory();
        AgentCallbackContext context = contextWithQuery("hello", "u1");
        InvokeInputs inputs = (InvokeInputs) context.getInputs();
        inputs.setResult(new LinkedHashMap<>(Map.of("result_type", "interrupt")));

        rail(memory).afterInvoke(context).toCompletableFuture().join();

        assertFalse(memory.addMessagesCalled);
    }

    private static MemoryRail rail(RecordingMemory memory) {
        AgentMemoryConfig config = AgentMemoryConfig.builder()
                .memVariables(List.of(Param.string("profile", "profile", false)))
                .build();
        return new MemoryRail("scope-a", config, memory);
    }

    private static AgentCallbackContext contextWithQuery(String query, String userId) {
        InvokeInputs inputs = new InvokeInputs();
        inputs.setQuery(query);
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(inputs);
        context.getExtra().put("user_id", userId);
        return context;
    }

    private static MemResult mem(String content) {
        return new MemResult(new MemInfo("id", content, null, null), 1.0);
    }

    private static final class RecordingMemory extends LongTermMemory {
        private final Map<String, String> variables = new LinkedHashMap<>();
        private boolean getVariablesCalled;
        private List<MemResult> userMems = List.of();
        private List<MemResult> summaryMems = List.of();
        private RuntimeException searchError;
        private String searchUserMemQuery;
        private String searchUserMemUserId;
        private String searchUserMemScopeId;
        private int searchUserMemNum;
        private int searchUserHistorySummaryNum;
        private boolean addMessagesCalled;
        private List<BaseMessage> addedMessages = List.of();
        private String addUserId;
        private String addScopeId;
        private String addSessionId;
        private ZonedDateTime addTimestamp;

        @Override
        public FutureMap<String, String> getVariables(Object names, String userId, String scopeId) {
            getVariablesCalled = true;
            return FutureMap.completed(new LinkedHashMap<>(variables));
        }

        @Override
        public FutureList<MemResult> searchUserMem(
                String query, int num, String userId, String scopeId, double threshold) {
            if (searchError != null) {
                return FutureList.fromFuture(CompletableFuture.failedFuture(searchError));
            }
            searchUserMemQuery = query;
            searchUserMemNum = num;
            searchUserMemUserId = userId;
            searchUserMemScopeId = scopeId;
            return FutureList.completed(userMems);
        }

        @Override
        public FutureList<MemResult> searchUserHistorySummary(
                String query, int num, String userId, String scopeId, double threshold) {
            if (searchError != null) {
                return FutureList.fromFuture(CompletableFuture.failedFuture(searchError));
            }
            searchUserHistorySummaryNum = num;
            return FutureList.completed(summaryMems);
        }

        @Override
        public CompletableFuture<AddMemResult> addMessages(
                List<BaseMessage> messages,
                AgentMemoryConfig agentConfig,
                String userId,
                String scopeId,
                String sessionId,
                ZonedDateTime timestamp,
                boolean genMem,
                int genMemWithHistoryMsgNum) {
            addMessagesCalled = true;
            addedMessages = List.copyOf(messages);
            addUserId = userId;
            addScopeId = scopeId;
            addSessionId = sessionId;
            addTimestamp = timestamp;
            return CompletableFuture.completedFuture(new AddMemResult());
        }
    }
}
