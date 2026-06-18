/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent.rails;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.AgentRail;
import com.openjiuwen.core.single_agent.rail.InvokeInputs;

import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Agent rail that integrates long-term memory into the ReActAgent lifecycle.
 *
 * <p>Mirrors Python's {@code MemoryRail} in
 * {@code openjiuwen/core/application/llm_agent/rails/memory_rail.py}.</p>
 */
public class MemoryRail extends AgentRail {

    private static final String MEMORY_VARIABLES_KEY = "memory_variables";
    private static final String ORIGINAL_QUERY_KEY = "_original_query";

    private final String memScopeId;
    private final AgentMemoryConfig agentMemoryConfig;
    private final LongTermMemory memory;
    private final boolean enableLongTermMem;
    private final boolean enableFragmentMemory;
    private final boolean enableSummaryMemory;
    private final boolean enableMemVariables;
    private final List<Param> memVariablesConfig;

    public MemoryRail(String memScopeId, AgentMemoryConfig agentMemoryConfig) {
        this(memScopeId, agentMemoryConfig, new LongTermMemory());
    }

    public MemoryRail(String memScopeId, AgentMemoryConfig agentMemoryConfig, LongTermMemory memory) {
        this.memScopeId = memScopeId == null ? "" : memScopeId;
        this.agentMemoryConfig = agentMemoryConfig == null ? new AgentMemoryConfig() : agentMemoryConfig;
        this.memory = memory == null ? new LongTermMemory() : memory;
        this.enableLongTermMem = this.agentMemoryConfig.isEnableLongTermMem();
        this.enableFragmentMemory = this.agentMemoryConfig.isEnableFragmentMemory();
        this.enableSummaryMemory = this.agentMemoryConfig.isEnableSummaryMemory();
        this.memVariablesConfig = this.agentMemoryConfig.getMemVariables();
        this.enableMemVariables = !this.memVariablesConfig.isEmpty();
    }

    @Override
    public CompletionStage<Void> beforeInvoke(AgentCallbackContext context) {
        if (context == null || isTruthy(context.getExtra().get("is_resume"))) {
            return completed();
        }

        String userId = stringValue(context.getExtra().get("user_id"));
        if (userId.isEmpty()) {
            return completed();
        }

        String query = readQuery(context.getInputs());
        Map<String, Object> result = new LinkedHashMap<>();
        loadVariables(userId, result);
        loadLongTermMemory(userId, query, result);

        context.getExtra().put(MEMORY_VARIABLES_KEY, result);
        context.getExtra().put(ORIGINAL_QUERY_KEY, query);
        return completed();
    }

    @Override
    public CompletionStage<Void> afterInvoke(AgentCallbackContext context) {
        if (context == null) {
            return completed();
        }

        String userId = stringValue(context.getExtra().get("user_id"));
        if (userId.isEmpty()) {
            return completed();
        }

        Map<String, Object> result = readResult(context.getInputs());
        if (result == null || !"answer".equals(result.get("result_type"))) {
            return completed();
        }

        String query = stringValue(context.getExtra().get(ORIGINAL_QUERY_KEY));
        String output = stringValue(result.get("output"));
        List<BaseMessage> messageList = new ArrayList<>();
        if (!query.isEmpty()) {
            messageList.add(new UserMessage(query));
        }
        if (!output.isEmpty()) {
            messageList.add(new AssistantMessage(output));
        }
        if (messageList.isEmpty()) {
            return completed();
        }

        String conversationId = readConversationId(context.getInputs());
        memory.addMessages(
                messageList,
                agentMemoryConfig,
                userId,
                memScopeId,
                conversationId.isEmpty() ? "default_session" : conversationId,
                ZonedDateTime.now(),
                true,
                2
        ).whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                Loggers.MEMORY.info("memory rail task [{}] completed", "memory_rail_add_messages");
            } else {
                Loggers.MEMORY.exception("memory rail task [memory_rail_add_messages] failed", unwrap(throwable));
            }
        });
        return completed();
    }

    private void loadVariables(String userId, Map<String, Object> result) {
        if (!enableMemVariables) {
            return;
        }
        try {
            Map<String, String> variables = memory.getVariables(null, userId, memScopeId)
                    .toCompletableFuture()
                    .join();
            if (variables != null && !variables.isEmpty()) {
                Set<String> allowed = new LinkedHashSet<>();
                for (Param param : memVariablesConfig) {
                    if (param != null) {
                        allowed.add(param.getName());
                    }
                }
                Map<String, String> filtered = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : variables.entrySet()) {
                    if (allowed.contains(entry.getKey())) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                }
                result.put("sys_memory_variables", JsonUtils.safeJsonDumps(filtered));
            }
            Loggers.MEMORY.info("memory_variables: {}", variables);
        } catch (RuntimeException exception) {
            Loggers.MEMORY.error("MemoryRail: get_variables failed: {}", exception.getMessage());
        }
    }

    private void loadLongTermMemory(String userId, String query, Map<String, Object> result) {
        if (!enableLongTermMem) {
            return;
        }
        List<String> memoryContents = new ArrayList<>();
        try {
            if (enableFragmentMemory) {
                List<MemResult> mems = memory.searchUserMem(query, 10, userId, memScopeId, 0.0)
                        .toCompletableFuture()
                        .join();
                if (mems != null && !mems.isEmpty()) {
                    memoryContents.add("用户画像记忆：");
                    appendMemoryContents(memoryContents, mems);
                }
                Loggers.MEMORY.info("long_term_memory: {}", mems);
            }
            if (enableSummaryMemory) {
                List<MemResult> mems = memory.searchUserHistorySummary(query, 5, userId, memScopeId, 0.0)
                        .toCompletableFuture()
                        .join();
                if (mems != null && !mems.isEmpty()) {
                    memoryContents.add("摘要记忆：");
                    appendMemoryContents(memoryContents, mems);
                }
                Loggers.MEMORY.info("user_summary_memory: {}", mems);
            }
            result.put("sys_long_term_memory",
                    memoryContents.isEmpty() ? "[]" : JsonUtils.safeJsonDumps(memoryContents));
        } catch (RuntimeException exception) {
            Loggers.MEMORY.error("MemoryRail: search memory failed: {}", exception.getMessage());
            result.put("sys_long_term_memory", "[]");
        }
    }

    private void appendMemoryContents(List<String> memoryContents, List<MemResult> mems) {
        for (MemResult mem : mems) {
            if (mem != null && mem.getMemInfo() != null && mem.getMemInfo().getContent() != null) {
                memoryContents.add(mem.getMemInfo().getContent());
            }
        }
    }

    private String readQuery(Object inputs) {
        if (inputs instanceof InvokeInputs invokeInputs) {
            return stringValue(invokeInputs.getQuery());
        }
        if (inputs instanceof Map<?, ?> map) {
            return stringValue(map.get("query"));
        }
        return stringValue(readAttribute(inputs, "query"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readResult(Object inputs) {
        Object result;
        if (inputs instanceof InvokeInputs invokeInputs) {
            result = invokeInputs.getResult();
        } else if (inputs instanceof Map<?, ?> map) {
            result = map.get("result");
        } else {
            result = readAttribute(inputs, "result");
        }
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((key, value) -> typed.put(String.valueOf(key), value));
            return typed;
        }
        return null;
    }

    private String readConversationId(Object inputs) {
        if (inputs instanceof InvokeInputs invokeInputs) {
            return stringValue(invokeInputs.getConversationId());
        }
        if (inputs instanceof Map<?, ?> map) {
            return stringValue(map.get("conversation_id"));
        }
        return stringValue(readAttribute(inputs, "conversationId"));
    }

    private Object readAttribute(Object source, String name) {
        if (source == null || name == null || name.isBlank()) {
            return null;
        }
        String methodName = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String candidate : List.of(methodName, name)) {
            try {
                Method method = source.getClass().getMethod(candidate);
                if (method.getParameterCount() == 0) {
                    return method.invoke(source);
                }
            } catch (ReflectiveOperationException ignored) {
                // Mirrors Python hasattr/getattr-style access across translated DTOs.
            }
        }
        return null;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && !String.valueOf(value).isEmpty();
    }

    private String stringValue(Object value) {
        return value == null ? "" : Objects.toString(value, "");
    }

    private Throwable unwrap(Throwable throwable) {
        return throwable.getCause() == null ? throwable : throwable.getCause();
    }
}
