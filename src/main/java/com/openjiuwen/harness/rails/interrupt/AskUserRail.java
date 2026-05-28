/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Ask-user rail: interrupts tool execution and returns user input without
 * executing the underlying tool.
 * <p>
 * Supports multi-question mode with structured selection.
 * <p>
 * Mirrors Python's {@code AskUserRail} in
 * {@code openjiuwen.harness.rails.interrupt.ask_user_rail}.
 */
public class AskUserRail extends BaseInterruptRail {

    private static final Logger LOG = LoggerFactory.getLogger(AskUserRail.class);

    /** Default tool names this rail intercepts. */
    private static final Set<String> DEFAULT_TOOL_NAMES =
            Collections.singleton("ask_user");

    private List<Object> tools = new ArrayList<>();

    public AskUserRail() {
        this(null);
    }

    public AskUserRail(Iterable<String> toolNames) {
        super(toolNames != null ? toolNames : DEFAULT_TOOL_NAMES);
    }

    @Override
    public void init(Object agent) {
        LOG.info("[AskUserRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        tools.clear();
        LOG.info("[AskUserRail] Uninitialized");
    }

    @Override
    public InterruptDecision resolveInterrupt(Object ctx, Object toolCall,
                                               Object userInput,
                                               Map<String, Object> autoConfirmConfig) {
        // On first call (no user input), interrupt to ask the user
        if (userInput == null) {
            LOG.debug("[AskUserRail] No user input yet — interrupting to ask user");
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("message", "Please provide your answer");
            request.put("payload_schema", AskUserPayload.toSchema());
            return InterruptDecision.interrupt(request);
        }

        // Parse user input
        AskUserPayload payload;
        if (userInput instanceof AskUserPayload) {
            payload = (AskUserPayload) userInput;
        } else if (userInput instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) userInput;
            payload = AskUserPayload.fromMap(map);
        } else {
            LOG.warn("[AskUserRail] Unexpected user input type: {}", userInput.getClass());
            return InterruptDecision.approve();
        }

        // Build the tool result from user answers
        String resultJson = payload.toJsonString();
        LOG.debug("[AskUserRail] User answered — approving with result");
        return InterruptDecision.approve(resultJson);
    }

    // ── Payload model ────────────────────────────────────────────────

    /**
     * Payload for ask-user response.
     * <p>
     * Mirrors Python's {@code AskUserPayload}.
     */
    public static class AskUserPayload {
        private final Map<String, String> answers = new LinkedHashMap<>();

        public AskUserPayload() {
        }

        public AskUserPayload(Map<String, String> answers) {
            if (answers != null) {
                this.answers.putAll(answers);
            }
        }

        public Map<String, String> getAnswers() {
            return Collections.unmodifiableMap(answers);
        }

        public String toJsonString() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> e : answers.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        public static AskUserPayload fromMap(Map<String, Object> map) {
            AskUserPayload payload = new AskUserPayload();
            Object answersObj = map.get("answers");
            if (answersObj instanceof Map) {
                ((Map<String, Object>) answersObj).forEach((k, v) ->
                        payload.answers.put(k, v != null ? v.toString() : ""));
            }
            return payload;
        }

        public static Map<String, Object> toSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", Collections.singletonMap("answers",
                    Collections.singletonMap("type", "object")));
            return schema;
        }
    }
}
