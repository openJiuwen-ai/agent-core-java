/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.llm;

import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.session.AgentSessionApi;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backward-compatible facade mirroring Python's {@code LLMController}.
 */
public class LlmController {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LlmAgentConfig agentConfig;
    private ContextEngine contextEngine;
    private LlmEventHandler eventHandler;

    public LlmController() {
    }

    public LlmController(LlmAgentConfig config, ContextEngine contextEngine) {
        configure(config, contextEngine);
    }

    public void setupFromAgent(LlmAgent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent is required");
        }
        configure(agent.getAgentConfig(), agent.getContextEngine());
        eventHandler.setAbilityManager(agent.getAbilityManager());
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

    public void setLlmControllerPromptTemplate(List<Map<String, String>> promptTemplate) {
        ensureConfigured();
        eventHandler.setPromptTemplate(promptTemplate);
    }

    public void setPromptTemplate(List<Map<String, String>> promptTemplate) {
        setLlmControllerPromptTemplate(promptTemplate);
    }

    public LlmAgentConfig getAgentConfig() {
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

    private void configure(LlmAgentConfig config, ContextEngine contextEngine) {
        this.agentConfig = config;
        this.contextEngine = contextEngine != null
                ? contextEngine
                : new ContextEngine(ContextEngineConfig.builder().build());
        this.eventHandler = config != null ? new LlmEventHandler(config, this.contextEngine) : null;
    }

    private void ensureConfigured() {
        if (eventHandler == null) {
            throw new IllegalStateException("LlmController is not configured with agent config");
        }
    }
}
