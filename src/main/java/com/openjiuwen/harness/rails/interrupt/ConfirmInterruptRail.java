/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Confirm rail: only proceeds when the user explicitly approves.
 * <p>
 * Supports auto-confirm configuration per tool name.
 * <p>
 * Mirrors Python's {@code ConfirmInterruptRail} in
 * {@code openjiuwen.harness.rails.interrupt.confirm_rail}.
 */
public class ConfirmInterruptRail extends BaseInterruptRail {

    private static final Logger LOG = LoggerFactory.getLogger(ConfirmInterruptRail.class);

    /** Default confirmation message. */
    public static final String DEFAULT_MESSAGE = "Please approve or reject?";

    private final ConfirmRequest request;

    public ConfirmInterruptRail() {
        this(null);
    }

    public ConfirmInterruptRail(Iterable<String> toolNames) {
        super(toolNames);
        this.request = new ConfirmRequest();
    }

    public ConfirmInterruptRail(Iterable<String> toolNames, String message) {
        super(toolNames);
        this.request = new ConfirmRequest(message);
    }

    @Override
    public void init(Object agent) {
        LOG.info("[ConfirmInterruptRail] Initialized for tools: {}", getToolNames());
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[ConfirmInterruptRail] Uninitialized");
    }

    @Override
    public InterruptDecision resolveInterrupt(Object ctx, Object toolCall,
                                               Object userInput,
                                               Map<String, Object> autoConfirmConfig) {
        // Derive the auto-confirm key from tool call name
        String autoConfirmKey = getAutoConfirmKey(toolCall);

        // On first call (no user input), check auto-confirm or interrupt
        if (userInput == null) {
            if (isAutoConfirmed(autoConfirmConfig, autoConfirmKey)) {
                LOG.debug("[ConfirmInterruptRail] Auto-confirmed for key: {}", autoConfirmKey);
                return InterruptDecision.approve();
            }
            // Interrupt to ask the user
            Map<String, Object> interruptRequest = new LinkedHashMap<>();
            interruptRequest.put("message", request.getMessage());
            interruptRequest.put("payload_schema", ConfirmPayload.toSchema());
            interruptRequest.put("auto_confirm_key", autoConfirmKey);
            return InterruptDecision.interrupt(interruptRequest);
        }

        // Parse user input as ConfirmPayload
        ConfirmPayload payload;
        if (userInput instanceof ConfirmPayload) {
            payload = (ConfirmPayload) userInput;
        } else if (userInput instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) userInput;
            payload = ConfirmPayload.fromMap(map);
        } else {
            LOG.warn("[ConfirmInterruptRail] Unexpected user input type: {} — rejecting",
                    userInput.getClass());
            return InterruptDecision.reject("Unexpected input format");
        }

        if (payload.isApproved()) {
            LOG.debug("[ConfirmInterruptRail] Approved with feedback: {}", payload.getFeedback());
            return InterruptDecision.approve();
        } else {
            LOG.debug("[ConfirmInterruptRail] Rejected with feedback: {}", payload.getFeedback());
            return InterruptDecision.reject(payload.getFeedback());
        }
    }

    /** Derive auto-confirm key from a tool call object. */
    protected String getAutoConfirmKey(Object toolCall) {
        if (toolCall == null) {
            return "unknown";
        }
        try {
            return (String) toolCall.getClass().getMethod("getName").invoke(toolCall);
        } catch (Exception e) {
            return toolCall.getClass().getSimpleName();
        }
    }

    // ── Inner models ─────────────────────────────────────────────────

    /** Confirmation request configuration. */
    public static class ConfirmRequest {
        private final String message;

        public ConfirmRequest() {
            this(DEFAULT_MESSAGE);
        }

        public ConfirmRequest(String message) {
            this.message = message != null ? message : DEFAULT_MESSAGE;
        }

        public String getMessage() {
            return message;
        }
    }

    /** Payload for user confirmation response. */
    public static class ConfirmPayload {
        private final boolean approved;
        private final String feedback;
        private final boolean autoConfirm;

        public ConfirmPayload(boolean approved, String feedback, boolean autoConfirm) {
            this.approved = approved;
            this.feedback = feedback != null ? feedback : "";
            this.autoConfirm = autoConfirm;
        }

        public boolean isApproved() {
            return approved;
        }

        public String getFeedback() {
            return feedback;
        }

        public boolean isAutoConfirm() {
            return autoConfirm;
        }

        @SuppressWarnings("unchecked")
        public static ConfirmPayload fromMap(Map<String, Object> map) {
            boolean approved = Boolean.TRUE.equals(map.get("approved"));
            String feedback = map.containsKey("feedback") ? String.valueOf(map.get("feedback")) : "";
            boolean autoConfirm = Boolean.TRUE.equals(map.get("auto_confirm"));
            return new ConfirmPayload(approved, feedback, autoConfirm);
        }

        public static Map<String, Object> toSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("approved", Collections.singletonMap("type", "boolean"));
            props.put("feedback", Collections.singletonMap("type", "string"));
            props.put("auto_confirm", Collections.singletonMap("type", "boolean"));
            schema.put("properties", props);
            schema.put("required", Collections.singletonList("approved"));
            return schema;
        }
    }
}
