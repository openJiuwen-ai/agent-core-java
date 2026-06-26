/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.harness.rails.CallbackContext;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Confirm rail that interrupts configured tool calls until approval is supplied.
 *
 * <p>Mirrors Python's {@code ConfirmInterruptRail}, {@code ConfirmPayload}, and
 * {@code ConfirmRequest} in
 * {@code openjiuwen/harness/rails/interrupt/confirm_rail.py}.</p>
 */
public class ConfirmInterruptRail extends BaseInterruptRail {

    private final ConfirmRequest request = new ConfirmRequest();

    public ConfirmInterruptRail() {
        super();
    }

    public ConfirmInterruptRail(Collection<String> toolNames) {
        super(toolNames);
    }

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        if (ctx != null && getTools().contains(String.valueOf(ctx.get("tool_name")))) {
            ctx.put("interrupt_kind", "confirm");
        }
        super.beforeToolCall(ctx);
    }

    public ConfirmRequest getRequest() {
        return request;
    }

    public InterruptDecision resolveInterrupt(
            CallbackContext ctx,
            Object toolCall,
            Object userInput,
            Map<String, Object> autoConfirmConfig) {
        String autoConfirmKey = getAutoConfirmKey(toolCall);
        if (userInput == null) {
            if (isAutoConfirmed(autoConfirmConfig, autoConfirmKey)) {
                return new ApproveResult();
            }
            return new InterruptResult(new InterruptRequest(
                    request.message(),
                    request.payloadSchema(),
                    autoConfirmKey));
        }

        ConfirmPayload payload = ConfirmPayload.from(userInput);
        if (payload == null) {
            return new InterruptResult(new InterruptRequest(
                    request.message(),
                    request.payloadSchema(),
                    autoConfirmKey));
        }
        if (payload.approved()) {
            return new ApproveResult();
        }
        return new RejectResult(payload.feedback().isBlank()
                ? "User feedback: rejected\n action"
                : payload.feedback());
    }

    protected String getAutoConfirmKey(Object toolCall) {
        Object name = readProperty(toolCall, "name", "getName");
        return name == null ? "" : String.valueOf(name);
    }

    public static boolean isAutoConfirmed(Map<String, Object> config, String key) {
        if (config == null || key == null) {
            return false;
        }
        return Boolean.TRUE.equals(config.get(key));
    }

    private static Object readProperty(Object target, String fieldName, String getterName) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(fieldName);
        }
        try {
            return target.getClass().getMethod(getterName).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * User confirmation response payload.
     *
     * <p>Mirrors Python's {@code ConfirmPayload} in
     * {@code openjiuwen/harness/rails/interrupt/confirm_rail.py}.</p>
     *
     * @param approved whether the tool call is approved
     * @param feedback rejection feedback
     * @param autoConfirm whether the response requests future auto-confirmation
     */
    public record ConfirmPayload(boolean approved, String feedback, boolean autoConfirm) {

        public static ConfirmPayload from(Object raw) {
            if (raw instanceof ConfirmPayload payload) {
                return payload;
            }
            if (raw instanceof Map<?, ?> map) {
                return new ConfirmPayload(
                        Boolean.TRUE.equals(map.get("approved")),
                        stringValue(map.get("feedback")),
                        Boolean.TRUE.equals(firstPresent(map, "auto_confirm", "autoConfirm")));
            }
            return null;
        }

        public static Map<String, Object> toSchema() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("approved", Map.of("type", "boolean"));
            properties.put("feedback", Map.of("type", "string", "default", ""));
            properties.put("auto_confirm", Map.of("type", "boolean", "default", false));
            return Map.of(
                    "type", "object",
                    "properties", properties,
                    "required", java.util.List.of("approved"));
        }

        private static Object firstPresent(Map<?, ?> map, String first, String second) {
            return map.containsKey(first) ? map.get(first) : map.get(second);
        }

        private static String stringValue(Object value) {
            return value == null ? "" : String.valueOf(value);
        }
    }

    /**
     * Confirmation request configuration.
     *
     * <p>Mirrors Python's {@code ConfirmRequest} in
     * {@code openjiuwen/harness/rails/interrupt/confirm_rail.py}.</p>
     *
     * @param message message shown to the user
     * @param payloadSchema JSON-like payload schema
     */
    public record ConfirmRequest(String message, Map<String, Object> payloadSchema) {

        public ConfirmRequest() {
            this("Please approve or reject?", ConfirmPayload.toSchema());
        }
    }
}
