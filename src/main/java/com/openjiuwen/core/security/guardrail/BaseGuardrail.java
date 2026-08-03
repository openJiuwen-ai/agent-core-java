/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.openjiuwen.core.common.exception.GuardrailError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.DecoratorFramework;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Mirrors Python's {@code BaseGuardrail} in
 * {@code openjiuwen/core/security/guardrail/guardrail.py}.
 */
public abstract class BaseGuardrail {

    public static final List<Object> DEFAULT_EVENTS = List.of();
    public static final int DEFAULT_PRIORITY = 100;
    public static final String NAMESPACE = "guardrail";

    private static final LoggerProtocol LOGGER = Loggers.COMMON;

    private final List<Object> events = new ArrayList<>();
    private final List<Object> registeredEvents = new ArrayList<>();
    private final Map<Object, Function<Map<String, Object>, Object>> registeredCallbacks = new LinkedHashMap<>();

    private final int priority;
    private final String namespace;

    private GuardrailBackend backend;
    private DecoratorFramework framework;
    private boolean enableLogging;

    protected BaseGuardrail() {
        this(null, null, null, true);
    }

    protected BaseGuardrail(List<?> events, GuardrailBackend backend, boolean enableLogging) {
        this(events, backend, null, enableLogging);
    }

    protected BaseGuardrail(GuardrailBackend backend, List<?> events, boolean enableLogging) {
        this(events, backend, null, enableLogging);
    }

    protected BaseGuardrail(List<?> events, GuardrailBackend backend, Integer priority, boolean enableLogging) {
        this.backend = backend;
        this.priority = priority != null ? priority : resolveDefaultPriority();
        this.namespace = resolveNamespace();
        this.enableLogging = enableLogging;

        if (events != null) {
            this.events.addAll(copyEvents(events));
        } else {
            this.events.addAll(resolveDefaultEvents());
        }
    }

    public List<Object> listenEvents() {
        return new ArrayList<>(events);
    }

    public BaseGuardrail withEvents(List<?> events) {
        this.events.clear();
        if (events != null) {
            this.events.addAll(copyEvents(events));
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

    public List<Object> getRegisteredEvents() {
        return new ArrayList<>(registeredEvents);
    }

    public boolean isEventRegistered(Object event) {
        return registeredEvents.contains(event);
    }

    public int getPriority() {
        return priority;
    }

    public String getNamespace() {
        return namespace;
    }

    public boolean isEnableLogging() {
        return enableLogging;
    }

    public void setEnableLogging(boolean enableLogging) {
        this.enableLogging = enableLogging;
    }

    public abstract GuardrailContext extractContext(Object event, Object[] args, Map<String, Object> kwargs);

    public GuardrailResult detect(Object event, Object[] args, Map<String, Object> kwargs) throws Exception {
        if (backend == null) {
            if (enableLogging) {
                LOGGER.error("No backend configured for {}", getClass().getSimpleName());
            }
            throw new IllegalStateException(
                    "No backend configured for " + getClass().getSimpleName() + ". Use setBackend() to set one."
            );
        }

        if (enableLogging) {
            LOGGER.info("Guardrail detection started for event '{}'", event);
        }

        GuardrailContext context = extractContext(event, args != null ? args.clone() : new Object[0], safeKwargs(kwargs));

        if (enableLogging) {
            LOGGER.debug("Analyzing data with backend: {}", backend.getClass().getSimpleName());
        }

        RiskAssessment assessment = backend.analyze(context);

        if (enableLogging) {
            if (assessment.isHasRisk()) {
                LOGGER.warning(
                        "Guardrail detected risk: {} (level: {}) for event '{}'",
                        assessment.getRiskType(),
                        assessment.getRiskLevel(),
                        event
                );
            } else {
                LOGGER.info("Guardrail passed for event '{}'", event);
            }
        }

        return new GuardrailResult(
                !assessment.isHasRisk(),
                assessment.getRiskLevel(),
                assessment.getRiskType(),
                assessment.getDetails(),
                null
        );
    }

    public void register(DecoratorFramework framework) {
        this.framework = Objects.requireNonNull(framework, "framework");

        if (enableLogging) {
            LOGGER.info(
                    "Registering guardrail {} for events: {}",
                    getClass().getSimpleName(),
                    listenEvents()
            );
        }

        for (Object event : listenEvents()) {
            Function<Map<String, Object>, Object> callback = kwargs -> {
                try {
                    return detectCallback(event, internalArgs(kwargs), safeKwargs(kwargs));
                } catch (RuntimeException runtimeException) {
                    throw runtimeException;
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            };

            framework.registerSync(
                    String.valueOf(event),
                    callback,
                    priority,
                    false,
                    namespace,
                    defaultTags(),
                    Collections.emptyList(),
                    null,
                    null,
                    0,
                    0.0d,
                    null,
                    "_detect_callback_" + event
            );

            registeredEvents.add(event);
            registeredCallbacks.put(event, callback);

            if (enableLogging) {
                LOGGER.info("Registered callback for event '{}' -> _detect_callback_{}", event, event);
            }
        }
    }

    public void unregister() {
        if (framework != null) {
            Map<String, List<CallbackInfo>> callbacksByEvent = framework.getCallbacks();
            for (Object event : registeredEvents) {
                Function<Map<String, Object>, Object> callback = registeredCallbacks.get(event);
                if (callback == null) {
                    continue;
                }
                List<CallbackInfo> callbacks = callbacksByEvent.get(String.valueOf(event));
                if (callbacks == null) {
                    continue;
                }
                callbacks.removeIf(info -> info.getCallback() == callback);
                if (callbacks.isEmpty()) {
                    callbacksByEvent.remove(String.valueOf(event));
                }
            }
        }

        registeredEvents.clear();
        registeredCallbacks.clear();
        framework = null;
    }

    protected void addRegisteredEvent(Object event) {
        registeredEvents.add(event);
    }

    protected Object detectCallback(Object event, Object[] args, Map<String, Object> kwargs) throws Exception {
        if (enableLogging) {
            LOGGER.info("Guardrail callback triggered for event '{}'", event);
        }

        GuardrailResult result = detect(event, args, kwargs);
        if (result.isSafe()) {
            return null;
        }

        Map<String, Object> riskInfo = new LinkedHashMap<>();
        riskInfo.put("risk_type", result.getRiskType() != null ? result.getRiskType() : "unknown");
        riskInfo.put("risk_level", result.getRiskLevel() != null ? result.getRiskLevel().name() : "UNKNOWN");
        riskInfo.put("event", String.valueOf(event));
        if (result.getDetails() != null) {
            riskInfo.putAll(result.getDetails());
        }

        if (enableLogging) {
            LOGGER.warning(
                    "Guardrail blocked event '{}': {} risk detected",
                    event,
                    result.getRiskType() != null ? result.getRiskType() : "unknown"
            );
        }

        if (result.getRiskLevel() == RiskLevel.CRITICAL) {
            throw new AbortError(
                    "Critical security risk detected: "
                            + (result.getRiskType() != null ? result.getRiskType() : "unknown"),
                    null,
                    riskInfo
            );
        }

        throw new GuardrailError(
                StatusCode.GUARDRAIL_BLOCKED,
                "Guardrail blocked: "
                        + (result.getRiskType() != null ? result.getRiskType() : "unknown")
                        + " risk detected",
                riskInfo,
                null,
                riskInfo
        );
    }

    private List<Object> resolveDefaultEvents() {
        Object value = resolveStaticField("DEFAULT_EVENTS");
        if (value instanceof Iterable<?> iterable) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : iterable) {
                resolved.add(item);
            }
            return resolved;
        }
        return new ArrayList<>();
    }

    private int resolveDefaultPriority() {
        Object value = resolveStaticField("DEFAULT_PRIORITY");
        return value instanceof Number number ? number.intValue() : DEFAULT_PRIORITY;
    }

    private String resolveNamespace() {
        Object value = resolveStaticField("NAMESPACE");
        return value instanceof String text && !text.isBlank() ? text : NAMESPACE;
    }

    private Object resolveStaticField(String fieldName) {
        try {
            Field field = getClass().getField(fieldName);
            if (!Modifier.isStatic(field.getModifiers())) {
                return null;
            }
            return field.get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Set<String> defaultTags() {
        Set<String> tags = new LinkedHashSet<>();
        tags.add("guardrail");
        tags.add(getClass().getSimpleName());
        return tags;
    }

    private static List<Object> copyEvents(List<?> events) {
        List<Object> copied = new ArrayList<>();
        copied.addAll(events);
        return copied;
    }

    private static Object[] internalArgs(Map<String, Object> kwargs) {
        Object args = kwargs != null ? kwargs.get("_args") : null;
        return args instanceof Object[] values ? values : new Object[0];
    }

    private static Map<String, Object> safeKwargs(Map<String, Object> kwargs) {
        return kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
    }
}
