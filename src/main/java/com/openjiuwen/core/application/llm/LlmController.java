/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.legacy.config.LegacyReActAgentConfig;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backward-compatible facade.
 *
 * <p>Mirrors Python's {@code LLMController} in
 * {@code openjiuwen/core/application/llm_agent/llm_controller.py}.</p>
 */
public class LlmController {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LegacyReActAgentConfig agentConfig;
    private ContextEngine contextEngine;
    private LlmEventHandler eventHandler;

    public LlmController() {
    }

    public LlmController(LegacyReActAgentConfig config, ContextEngine contextEngine) {
        configure(config, contextEngine);
    }

    public void setupFromAgent(Object agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent is required");
        }
        Object rawConfig = invokeNoArg(agent, "getAgentConfig");
        if (!(rawConfig instanceof LegacyReActAgentConfig config)) {
            throw new IllegalArgumentException("agent config must be LegacyReActAgentConfig");
        }
        Object rawContextEngine = invokeNoArg(agent, "getContextEngine");
        configure(config, rawContextEngine instanceof ContextEngine context ? context : null);
        eventHandler.setAbilityManager(invokeNoArg(agent, "getAbilityManager"));
    }

    public Map<String, Object> handleEvent(Event event, AgentSessionApi session) {
        ensureConfigured();
        return eventHandler.handleInput(new EventHandlerInput(event, session));
    }

    public Event createMessage(Map<String, Object> inputs) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (inputs != null) {
            normalized.putAll(inputs);
        }
        if (!normalized.containsKey("query") && normalized.containsKey("content")) {
            normalized.put("query", normalized.get("content"));
        }
        normalized.putIfAbsent("query", "");
        return InputEvent.fromUserInput(normalized);
    }

    public void setLlmControllerPromptTemplate(List<? extends Map<String, ?>> promptTemplate) {
        ensureConfigured();
        eventHandler.setPromptTemplate(promptTemplate);
    }

    public void setPromptTemplate(List<? extends Map<String, ?>> promptTemplate) {
        setLlmControllerPromptTemplate(promptTemplate);
    }

    public LegacyReActAgentConfig getAgentConfig() {
        return agentConfig;
    }

    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    public LlmEventHandler getEventHandler() {
        ensureConfigured();
        return eventHandler;
    }

    public static String convertTimestamp(String utcTimestamp) {
        if (utcTimestamp == null || utcTimestamp.isBlank()) {
            return utcTimestamp;
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(utcTimestamp, TIMESTAMP_FORMATTER);
            return parsed.atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            return utcTimestamp;
        }
    }

    private void configure(LegacyReActAgentConfig config, ContextEngine contextEngine) {
        this.agentConfig = config;
        this.contextEngine = contextEngine != null
                ? contextEngine
                : new ContextEngine(new ContextEngineConfig());
        this.eventHandler = config != null ? new LlmEventHandler(config, this.contextEngine) : null;
    }

    private void ensureConfigured() {
        if (eventHandler == null) {
            throw new IllegalStateException("LlmController is not configured with agent config");
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
