/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallback;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.interrupt.ApproveResult;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.rails.interrupt.InterruptRailSupport;
import com.openjiuwen.harness.rails.interrupt.InterruptResult;
import com.openjiuwen.harness.rails.interrupt.RejectResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private static final Set<String> REQUEST_FIELDS = Set.of(
            "message", "payload_schema", "auto_confirm_key", "ui_options");

    private final Set<String> toolNames = new LinkedHashSet<>();
    private final Set<String> supportedEvents = new LinkedHashSet<>();

    public BaseSecurityRail() {
        this(Set.of());
    }

    public BaseSecurityRail(Iterable<String> toolNames) {
        setPriority(90);
        addTools(toolNames);
    }

    @Override
    public Map<AgentCallbackEvent, AgentCallback> getCallbacks() {
        Map<AgentCallbackEvent, AgentCallback> callbacks = new EnumMap<>(super.getCallbacks());
        callbacks.put(AgentCallbackEvent.BEFORE_INVOKE, context -> {
            ToolInterruptionState.railState(context);
            beforeInvoke(context);
            return completed();
        });
        return callbacks;
    }

    @Override
    public void beforeToolCall(AgentCallbackContext context) {
        if (!supportedEvents.contains(BEFORE_TOOL_CALL)
                || !(context.getInputs() instanceof ToolCallInputs inputs)
                || !(inputs.getToolCall() instanceof ToolCall call)) {
            return;
        }
        String railId = getClass().getName();
        InterruptRailSupport.evaluate(context, call, railId, () -> resolveInterrupt(context, call,
                InterruptRailSupport.userInput(context, call, railId).orElse(null)));
    }

    /**
     * Resolve a native tool security decision using the existing engine and host hooks.
     *
     * @param context current callback context
     * @param toolCall tool call being checked
     * @param userInput matched confirmation response, or null
     * @return decision to approve, reject, or pause this call
     * @since 0.1.17
     */
    protected InterruptDecision resolveInterrupt(AgentCallbackContext context, ToolCall toolCall, Object userInput) {
        CallbackContext callback = toCallbackContext(context);
        Map<String, Object> autoConfirm = InterruptRailSupport.autoConfirmConfig(context);
        callback.put("auto_confirm_config", autoConfirm);
        SecurityCheckContext security = new SecurityCheckContext(callback, BEFORE_TOOL_CALL,
                userInput, autoConfirm, toolCall.getId());
        SecurityDecision decision = runSecurityCheck(security);
        if (decision instanceof SecurityInterrupt pending) {
            return interrupt(toInterruptRequest(pending.request()));
        }
        applySecurityDecision(security, decision);
        applyCallbackContext(context, callback);
        saveAutoConfirm(context, callback, autoConfirm);
        if (decision instanceof SecurityReject) {
            ToolMessage message = null;
            if (callback.get("tool_msg") instanceof ToolMessage toolMessage) {
                message = toolMessage;
            }
            return new RejectResult(callback.get("tool_result"), message);
        }
        if (decision instanceof SecurityAllow allowed) {
            return new ApproveResult(allowed.newArgs());
        }
        return new ApproveResult();
    }

    /**
     * Pause a native tool call for security approval.
     *
     * @param request native approval request
     * @return pending decision
     * @since 0.1.17
     */
    public InterruptResult interrupt(InterruptRequest request) {
        return new InterruptResult(request);
    }

    private static InterruptRequest toInterruptRequest(Map<String, Object> values) {
        InterruptRequest request = new InterruptRequest();
        request.setMessage(Objects.toString(values.get("message"), ""));
        request.setAutoConfirmKey(Objects.toString(values.get("auto_confirm_key"), ""));
        if (values.get("payload_schema") instanceof Map<?, ?> schema) {
            Map<String, Object> copied = new LinkedHashMap<>();
            schema.forEach((key, value) -> copied.put(String.valueOf(key), value));
            request.setPayloadSchema(copied);
        }
        if (values.get("ui_options") instanceof List<?> options) {
            List<Map<String, Object>> copied = new ArrayList<>();
            for (Object option : options) {
                if (option instanceof Map<?, ?> map) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    map.forEach((key, value) -> item.put(String.valueOf(key), value));
                    copied.add(item);
                }
            }
            request.setUiOptions(copied);
        }
        values.forEach((key, value) -> {
            if (!REQUEST_FIELDS.contains(key)) {
                request.putExtraField(key, value);
            }
        });
        return request;
    }

    private static void saveAutoConfirm(AgentCallbackContext context, CallbackContext callback,
                                        Map<String, Object> previous) {
        Object current = callback.get("auto_confirm_config");
        if (context.getSession() == null || !(current instanceof Map<?, ?> map) || previous.equals(map)) {
            return;
        }
        Map<String, Object> config = new LinkedHashMap<>();
        map.forEach((key, value) -> config.put(String.valueOf(key), value));
        context.getSession().updateState(Map.of(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY, config));
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
            // Surface the rejection to the LLM as the tool result/message; otherwise the
            // skipped tool call yields a null result and the denial reason never reaches
            // the model (Python parity: the deny reason is returned as the tool output).
            Object rejectResult = securityReject.result() != null ? securityReject.result() : message;
            ctx.put("tool_result", rejectResult);
            Object toolMessage = securityReject.toolMessage();
            if (toolMessage == null) {
                toolMessage = new ToolMessage(String.valueOf(rejectResult),
                        toolCallIdOf(ctx.get("tool_call")),
                        String.valueOf(ctx.getValues().getOrDefault("tool_name", "")));
            }
            ctx.put("tool_msg", toolMessage);
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

    private static String toolCallIdOf(Object toolCall) {
        if (toolCall instanceof ToolCall call && call.getId() != null) {
            return call.getId();
        }
        if (toolCall instanceof Map<?, ?> map && map.get("id") != null) {
            return String.valueOf(map.get("id"));
        }
        return "";
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
