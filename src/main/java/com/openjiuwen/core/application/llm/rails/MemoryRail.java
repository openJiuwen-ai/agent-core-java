/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm.rails;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.EventInputs;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent rail that loads memory before invoke and writes answer turns after invoke.
 *
 * <p>Mirrors Python's {@code MemoryRail} in
 * {@code openjiuwen.core.application.llm_agent.rails.memory_rail}.</p>
 */
public class MemoryRail extends AgentRail {

    static final String MEMORY_VARIABLES_KEY = "memory_variables";
    static final String ORIGINAL_QUERY_KEY = "_original_query";

    private static final LoggerProtocol LOGGER = Loggers.MEMORY;
    private static final String SYS_MEMORY_VARIABLES = "sys_memory_variables";
    private static final String SYS_LONG_TERM_MEMORY = "sys_long_term_memory";
    private static final String DEFAULT_SESSION = "default_session";
    private static final double SEARCH_THRESHOLD = 0.0;

    private final String memScopeId;
    private final AgentMemoryConfig agentMemoryConfig;
    private final MemoryClient memory;
    private final boolean enableLongTermMem;
    private final boolean enableFragmentMemory;
    private final boolean enableSummaryMemory;
    private final boolean enableMemVariables;
    private final List<Param> memVariablesConfig;

    /**
     * Create a memory rail bound to one memory scope.
     *
     * @param memScopeId the memory scope id
     * @param agentMemoryConfig memory feature configuration
     */
    public MemoryRail(String memScopeId, AgentMemoryConfig agentMemoryConfig) {
        this(memScopeId, agentMemoryConfig, new LongTermMemoryClient(LongTermMemory.getInstance()));
    }

    MemoryRail(String memScopeId, AgentMemoryConfig agentMemoryConfig, MemoryClient memory) {
        this.memScopeId = memScopeId;
        this.agentMemoryConfig = agentMemoryConfig != null ? agentMemoryConfig : new AgentMemoryConfig();
        this.memory = memory;
        this.memVariablesConfig = this.agentMemoryConfig.getMemVariables() != null
                ? this.agentMemoryConfig.getMemVariables()
                : List.of();
        this.enableLongTermMem = this.agentMemoryConfig.isEnableLongTermMem();
        this.enableFragmentMemory = this.agentMemoryConfig.isEnableFragmentMemory();
        this.enableSummaryMemory = this.agentMemoryConfig.isEnableSummaryMemory();
        this.enableMemVariables = !this.memVariablesConfig.isEmpty();
    }

    /**
     * Load memory variables and long-term memory search results into {@code ctx.extra}.
     *
     * @param ctx callback context
     */
    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        Map<String, Object> extra = ensureExtra(ctx);
        if (isTruthy(extra.get("is_resume"))) {
            return;
        }

        String userId = firstNonBlank(extra.get("user_id"), extra.get("userId"));
        if (userId.isBlank()) {
            return;
        }

        String query = extractQuery(ctx.getInputs());
        Map<String, Object> result = new LinkedHashMap<>();

        if (enableMemVariables) {
            loadMemoryVariables(userId, result);
        }
        if (enableLongTermMem) {
            loadLongTermMemory(userId, query, result);
        }

        extra.put(MEMORY_VARIABLES_KEY, result);
        extra.put(ORIGINAL_QUERY_KEY, query);
    }

    /**
     * Write answer turns back to long-term memory.
     *
     * @param ctx callback context
     */
    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        Map<String, Object> extra = ensureExtra(ctx);
        String userId = firstNonBlank(extra.get("user_id"), extra.get("userId"));
        if (userId.isBlank()) {
            return;
        }

        Map<String, Object> result = extractResult(ctx.getInputs());
        if (result == null || !"answer".equals(String.valueOf(result.getOrDefault("result_type", "")))) {
            return;
        }

        String query = stringValue(extra.get(ORIGINAL_QUERY_KEY));
        String output = stringValue(result.get("output"));
        List<BaseMessage> messages = new ArrayList<>();
        if (!query.isBlank()) {
            messages.add(new UserMessage(query));
        }
        if (!output.isBlank()) {
            messages.add(new AssistantMessage(output));
        }
        if (messages.isEmpty()) {
            return;
        }

        String conversationId = extractConversationId(ctx.getInputs());
        if (conversationId.isBlank()) {
            conversationId = DEFAULT_SESSION;
        }

        try {
            memory.addMessages(messages, agentMemoryConfig, userId, memScopeId, conversationId);
            LOGGER.info("memory rail task [memory_rail_add_messages] completed");
        } catch (Exception e) {
            LOGGER.exception("memory rail task [memory_rail_add_messages] failed: {}", e, e.getMessage());
        }
    }

    private void loadMemoryVariables(String userId, Map<String, Object> result) {
        try {
            Map<String, String> variables = memory.getVariables(null, userId, memScopeId);
            if (variables != null && !variables.isEmpty()) {
                Set<String> allowed = allowedVariableNames();
                Map<String, String> filtered = new LinkedHashMap<>();
                variables.forEach((key, value) -> {
                    if (allowed.contains(key)) {
                        filtered.put(key, value);
                    }
                });
                result.put(SYS_MEMORY_VARIABLES, JsonUtils.safeJsonDumps(filtered, "{}"));
            }
            LOGGER.info("memory_variables: {}", variables);
        } catch (Exception e) {
            LOGGER.error("MemoryRail: get_variables failed: {}", e.getMessage());
        }
    }

    private void loadLongTermMemory(String userId, String query, Map<String, Object> result) {
        List<String> memoryContents = new ArrayList<>();
        try {
            if (enableFragmentMemory) {
                List<MemResult> mems = memory.searchUserMem(query, 10, userId, memScopeId, SEARCH_THRESHOLD);
                if (mems != null && !mems.isEmpty()) {
                    memoryContents.add("用户画像记忆：");
                    for (MemResult mem : mems) {
                        String content = mem != null && mem.getMemInfo() != null
                                ? mem.getMemInfo().getContent()
                                : "";
                        if (content != null) {
                            memoryContents.add(content);
                        }
                    }
                }
                LOGGER.info("long_term_memory: {}", mems);
            }
            if (enableSummaryMemory) {
                List<MemResult> mems = memory.searchUserHistorySummary(query, 5, userId, memScopeId, SEARCH_THRESHOLD);
                if (mems != null && !mems.isEmpty()) {
                    memoryContents.add("摘要记忆：");
                    for (MemResult mem : mems) {
                        String content = mem != null && mem.getMemInfo() != null
                                ? mem.getMemInfo().getContent()
                                : "";
                        if (content != null) {
                            memoryContents.add(content);
                        }
                    }
                }
                LOGGER.info("user_summary_memory: {}", mems);
            }
            result.put(SYS_LONG_TERM_MEMORY,
                    memoryContents.isEmpty() ? "[]" : JsonUtils.safeJsonDumps(memoryContents, "[]"));
        } catch (Exception e) {
            LOGGER.error("MemoryRail: search memory failed: {}", e.getMessage());
            result.put(SYS_LONG_TERM_MEMORY, "[]");
        }
    }

    private Set<String> allowedVariableNames() {
        Set<String> allowed = new HashSet<>();
        for (Param param : memVariablesConfig) {
            if (param != null && param.getName() != null) {
                allowed.add(param.getName());
            }
        }
        return allowed;
    }

    private static Map<String, Object> ensureExtra(AgentCallbackContext ctx) {
        Map<String, Object> extra = ctx.getExtra();
        if (extra == null) {
            extra = new HashMap<>();
            ctx.setExtra(extra);
        }
        return extra;
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0;
        }
        if (value instanceof String string) {
            return !string.isEmpty();
        }
        return true;
    }

    private static String firstNonBlank(Object first, Object second) {
        String firstValue = stringValue(first);
        return !firstValue.isBlank() ? firstValue : stringValue(second);
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static String extractQuery(EventInputs inputs) {
        if (inputs instanceof InvokeInputs invokeInputs) {
            return stringValue(invokeInputs.getQuery());
        }
        return stringValue(reflectGetter(inputs, "getQuery"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractResult(EventInputs inputs) {
        if (inputs instanceof InvokeInputs invokeInputs) {
            return invokeInputs.getResult();
        }
        Object value = reflectGetter(inputs, "getResult");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static String extractConversationId(EventInputs inputs) {
        if (inputs instanceof InvokeInputs invokeInputs) {
            return stringValue(invokeInputs.getConversationId());
        }
        return stringValue(reflectGetter(inputs, "getConversationId"));
    }

    private static Object reflectGetter(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    interface MemoryClient {
        Map<String, String> getVariables(Object names, String userId, String scopeId);

        List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold);

        List<MemResult> searchUserHistorySummary(String query, int num, String userId, String scopeId, double threshold);

        void addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig, String userId, String scopeId,
                         String sessionId);
    }

    private record LongTermMemoryClient(LongTermMemory delegate) implements MemoryClient {
        @Override
        public Map<String, String> getVariables(Object names, String userId, String scopeId) {
            return delegate.getVariables(names, userId, scopeId);
        }

        @Override
        public List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold) {
            return delegate.searchUserMem(query, num, userId, scopeId, threshold);
        }

        @Override
        public List<MemResult> searchUserHistorySummary(String query, int num, String userId, String scopeId,
                                                        double threshold) {
            return delegate.searchUserHistorySummary(query, num, userId, scopeId, threshold);
        }

        @Override
        public void addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig, String userId,
                                String scopeId, String sessionId) {
            delegate.addMessages(messages, agentConfig, userId, scopeId, sessionId);
        }
    }
}
