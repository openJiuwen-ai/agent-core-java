/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base rail for security checks.
 *
 * <p>Mirrors Python's {@code BaseSecurityRail} in
 * {@code openjiuwen/harness/rails/security/base_security_rail.py}.</p>
 */
public class BaseSecurityRail extends DeepAgentRail {

    public static final String BEFORE_INVOKE = "before_invoke";
    public static final String AFTER_INVOKE = "after_invoke";
    public static final String BEFORE_TOOL_CALL = "before_tool_call";
    public static final String AFTER_TOOL_CALL = "after_tool_call";
    public static final String BEFORE_MODEL_CALL = "before_model_call";
    public static final String AFTER_MODEL_CALL = "after_model_call";

    private final Set<String> toolNames = new LinkedHashSet<>();
    private final Set<String> supportedEvents = new LinkedHashSet<>();

    public BaseSecurityRail() {
        this(Set.of());
    }

    public BaseSecurityRail(Iterable<String> toolNames) {
        setPriority(90);
        addTools(toolNames);
    }

    public SecurityAllow allow() {
        return new SecurityAllow(null);
    }

    public SecurityAllow allow(String newArgs) {
        return new SecurityAllow(newArgs);
    }

    public SecurityAllow approve() {
        return allow();
    }

    public SecurityReject reject(String message) {
        return new SecurityReject(message);
    }

    public SecurityReject reject(String message, Object result, Object toolMessage) {
        return new SecurityReject(message, result, toolMessage);
    }

    public SecurityInterrupt interrupt(Map<String, Object> request, String subjectId) {
        return new SecurityInterrupt(request, subjectId);
    }

    public SecurityAlert alert(String message) {
        return new SecurityAlert(message, SecurityAlertLevel.WARNING, "security", "popup");
    }

    public SecurityAlert alert(
            String message,
            SecurityAlertLevel level,
            String alertType,
            String displayMode
    ) {
        return new SecurityAlert(message, level, alertType, displayMode);
    }

    public void addTool(String toolName) {
        if (toolName != null && !toolName.isBlank()) {
            toolNames.add(toolName);
        }
    }

    public void addTools(Iterable<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            addTool(name);
        }
    }

    public void addPolicy(String toolName, Object ignoredPolicy) {
        addTool(toolName);
    }

    public Set<String> getTools() {
        return new LinkedHashSet<>(toolNames);
    }

    public Set<String> getSupportedEvents() {
        return new LinkedHashSet<>(supportedEvents);
    }

    protected void setSupportedEvents(Iterable<String> events) {
        supportedEvents.clear();
        if (events == null) {
            return;
        }
        for (String event : events) {
            if (event != null && !event.isBlank()) {
                supportedEvents.add(event);
            }
        }
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        runIfSupported(ctx, BEFORE_INVOKE);
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        runIfSupported(ctx, AFTER_INVOKE);
    }

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        runIfSupported(ctx, BEFORE_TOOL_CALL);
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        runIfSupported(ctx, AFTER_TOOL_CALL);
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        runIfSupported(ctx, BEFORE_MODEL_CALL);
    }

    @Override
    public void afterModelCall(CallbackContext ctx) {
        runIfSupported(ctx, AFTER_MODEL_CALL);
    }

    protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
        return allow();
    }

    protected void applySecurityDecision(SecurityCheckContext securityCtx, SecurityDecision decision) {
        CallbackContext ctx = securityCtx.callbackContext();
        if (decision == null || decision instanceof SecurityAllow) {
            return;
        }
        if (decision instanceof SecurityAlert securityAlert) {
            appendAlert(ctx, securityAlert);
            return;
        }
        if (decision instanceof SecurityReject securityReject) {
            String message = securityReject.message();
            if (message == null || message.isBlank()) {
                message = securityReject.result() == null ? "Blocked by security rail" : String.valueOf(securityReject.result());
            }
            ctx.put("security_reject", securityReject);
            ctx.reject(message);
            return;
        }
        if (decision instanceof SecurityInterrupt securityInterrupt) {
            ctx.put("security_interrupt_request", securityInterrupt.request());
            ctx.put("security_interrupt_subject_id", securityInterrupt.subjectId());
            ctx.reject("Security approval required.");
        }
    }

    protected SecurityCheckContext buildSecurityContext(CallbackContext ctx, String event) {
        String subjectId = resolveSubjectId(ctx, event);
        return new SecurityCheckContext(
                ctx,
                event,
                getUserInput(ctx, subjectId),
                getAutoConfirmConfig(ctx),
                subjectId
        );
    }

    protected String resolveSubjectId(CallbackContext ctx, String event) {
        Object callId = ctx.get("tool_call_id");
        if (callId != null && !String.valueOf(callId).isBlank()) {
            return String.valueOf(callId);
        }
        if (BEFORE_TOOL_CALL.equals(event) || AFTER_TOOL_CALL.equals(event)) {
            Object toolName = ctx.get("tool_name");
            return toolName == null ? "" : String.valueOf(toolName);
        }
        return getClass().getSimpleName() + ":" + event;
    }

    protected Object getUserInput(CallbackContext ctx, String subjectId) {
        Object rawInput = ctx.get("resume_user_input");
        if (rawInput == null) {
            rawInput = ctx.get("user_input");
        }
        if (rawInput instanceof Map<?, ?> map && subjectId != null && map.containsKey(subjectId)) {
            return map.get(subjectId);
        }
        return rawInput;
    }

    protected Map<String, Object> getAutoConfirmConfig(CallbackContext ctx) {
        Object value = ctx.get("auto_confirm_config");
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private void runIfSupported(CallbackContext ctx, String event) {
        if (!supportedEvents.contains(event)) {
            return;
        }
        SecurityCheckContext securityCtx = buildSecurityContext(ctx, event);
        SecurityDecision decision = runSecurityCheck(securityCtx);
        applySecurityDecision(securityCtx, decision);
    }

    @SuppressWarnings("unchecked")
    private void appendAlert(CallbackContext ctx, SecurityAlert securityAlert) {
        Object rawAlerts = ctx.get("security_alerts");
        List<Object> alerts = rawAlerts instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", securityAlert.message());
        payload.put("level", securityAlert.level().value());
        payload.put("alert_type", securityAlert.alertType());
        payload.put("display_mode", securityAlert.displayMode());
        alerts.add(payload);
        ctx.put("security_alerts", alerts);
    }
}
