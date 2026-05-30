/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.openjiuwen.core.common.exception.GuardrailError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.callback.HookType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base class for guardrails that integrate with {@link CallbackFramework}.
 *
 * <p>Mirrors Python's {@code BaseGuardrail} in
 * {@code openjiuwen.core.security.guardrail.guardrail}.</p>
 */
public abstract class BaseGuardrail {

    protected static final LoggerProtocol LOGGER = Loggers.RUNNER;

    protected final List<String> events = new ArrayList<>();
    protected final List<String> registeredEvents = new ArrayList<>();
    protected final Map<String, Function<Map<String, Object>, Object>> registeredCallbacks = new ConcurrentHashMap<>();

    protected GuardrailBackend backend;
    protected CallbackFramework framework;
    protected boolean enableLogging = true;

    protected BaseGuardrail(GuardrailBackend backend, List<String> events, boolean enableLogging) {
        this.backend = backend;
        this.enableLogging = enableLogging;
        List<String> defaults = events != null ? events : defaultEvents();
        if (defaults != null) {
            this.events.addAll(defaults);
        }
    }

    protected abstract List<String> defaultEvents();

    public List<String> listenEvents() {
        return new ArrayList<>(events);
    }

    public BaseGuardrail withEvents(List<String> events) {
        this.events.clear();
        if (events != null) {
            this.events.addAll(events);
        }
        return this;
    }

    public BaseGuardrail setBackend(GuardrailBackend backend) {
        this.backend = backend;
        return this;
    }

    public GuardrailBackend getBackend() {
        return backend;
    }

    public List<String> getRegisteredEvents() {
        return new ArrayList<>(registeredEvents);
    }

    public boolean isEventRegistered(String event) {
        return registeredEvents.contains(event);
    }

    public boolean isEnableLogging() {
        return enableLogging;
    }

    public void setEnableLogging(boolean enableLogging) {
        this.enableLogging = enableLogging;
    }

    /**
     * Perform detection for an event. Subclasses may override.
     */
    public GuardrailResult detect(String eventName, Object[] args, Map<String, Object> kwargs) throws Exception {
        if (backend == null) {
            throw new IllegalStateException(
                    "No backend configured for " + getClass().getSimpleName()
                            + ". Either set a backend or override detect().");
        }

        Map<String, Object> analysisData = new LinkedHashMap<>();
        analysisData.put("event", eventName);
        analysisData.put("args", args == null ? List.of() : List.of(args));
        if (kwargs != null) {
            for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
                if (!"_args".equals(entry.getKey())) {
                    analysisData.put(entry.getKey(), entry.getValue());
                }
            }
        }

        RiskAssessment assessment = backend.analyze(analysisData);
        if (assessment == null || !assessment.isHasRisk()) {
            return GuardrailResult.pass(assessment != null ? assessment.getDetails() : null);
        }
        return GuardrailResult.block(
                assessment.getRiskLevel(),
                assessment.getRiskType(),
                assessment.getDetails(),
                null
        );
    }

    /**
     * Callback-framework wrapper around {@link #detect(String, Object[], Map)}.
     *
     * <p>Python keeps this separate as {@code _detect_callback}: {@code detect}
     * returns a {@link GuardrailResult}, while the callback wrapper turns risky
     * results into guardrail exceptions and returns {@code None} on safe input.</p>
     */
    protected Object detectCallback(String eventName, Object[] args, Map<String, Object> kwargs) {
        try {
            GuardrailResult result = detect(eventName, args, kwargs);
            if (result == null || result.isSafe()) {
                return null;
            }

            Map<String, Object> riskInfo = buildRiskInfo(eventName, result);
            String riskType = (String) riskInfo.get("risk_type");
            if (result.getRiskLevel() == RiskLevel.CRITICAL) {
                throw new AbortError("Critical security risk detected: " + riskType);
            }

            throw new GuardrailError(
                    StatusCode.GUARDRAIL_BLOCKED,
                    "Guardrail blocked: " + riskType + " risk detected",
                    riskInfo,
                    null,
                    riskInfo
            );
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    protected void addRegisteredEvent(String event) {
        registeredEvents.add(event);
    }

    private Map<String, Object> buildRiskInfo(String eventName, GuardrailResult result) {
        Map<String, Object> riskInfo = new LinkedHashMap<>();
        riskInfo.put("risk_type", result.getRiskType() == null ? "unknown" : result.getRiskType());
        riskInfo.put("risk_level", result.getRiskLevel() == null ? "UNKNOWN" : result.getRiskLevel().name());
        riskInfo.put("event", String.valueOf(eventName));
        if (result.getDetails() != null) {
            riskInfo.putAll(result.getDetails());
        }
        return riskInfo;
    }

    /**
     * Register this guardrail with a callback framework.
     */
    public void register(CallbackFramework framework) {
        this.framework = Objects.requireNonNull(framework, "framework");

        Consumer<Map<String, Object>> rethrowHook = hookData -> {
            Object error = hookData.get("_error");
            if (error instanceof RuntimeException runtimeException) {
                hookData.put("_raise", runtimeException);
            } else if (error instanceof Throwable throwable) {
                hookData.put("_raise", new RuntimeException(throwable));
            }
        };

        for (String event : listenEvents()) {
            framework.addHook(event, HookType.ERROR, rethrowHook);

            Function<Map<String, Object>, Object> callback = kwargs -> {
                Object[] args = kwargs != null && kwargs.get("_args") instanceof Object[] arr ? arr : new Object[0];
                return detectCallback(event, args, kwargs);
            };

            framework.register(event, callback, 100, false, "guardrail",
                    Set.of("guardrail", getClass().getSimpleName()),
                    null, null, null, 0, 0.0, null, callbackName(event));
            registeredCallbacks.put(event, callback);
            addRegisteredEvent(event);

            if (enableLogging) {
                LOGGER.info("Registered guardrail {} for event {}", getClass().getSimpleName(), event);
            }
        }
    }

    /**
     * Unregister this guardrail from the previously registered framework.
     */
    public void unregister() {
        if (framework == null) {
            return;
        }
        for (Map.Entry<String, Function<Map<String, Object>, Object>> entry : registeredCallbacks.entrySet()) {
            framework.unregister(entry.getKey(), entry.getValue());
        }
        registeredCallbacks.clear();
        registeredEvents.clear();
    }

    private String callbackName(String event) {
        return getClass().getSimpleName() + ":" + event;
    }
}
