/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Single-agent implementation backed by a controller object.
 *
 * <p>Mirrors Python's {@code ControllerAgent} in
 * {@code openjiuwen/core/single_agent/base.py}.</p>
 */
public class ControllerAgent extends BaseAgent {
    private Object controller;
    private final ContextEngine contextEngine;

    public ControllerAgent(AgentCard card, Object controller) {
        this(card, controller, null);
    }

    public ControllerAgent(AgentCard card, Object controller, Object config) {
        super(card);
        this.contextEngine = new ContextEngine(new ContextEngineConfig());
        this.controller = controller;
        setConfig(normalizeConfig(config));
        initializeController();
    }

    @Override
    public BaseAgent configure(Object config) {
        ControllerConfig controllerConfig = normalizeConfig(config);
        setConfig(controllerConfig);
        writeControllerConfig(controllerConfig);
        return this;
    }

    @Override
    public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
        if (controller == null) {
            return failed(new IllegalStateException(getClass().getSimpleName() + " has no controller"));
        }
        if (session == null) {
            return failed(new IllegalArgumentException("session is required"));
        }
        Object result = invokeController("invoke", toInputEvent(inputs), session, null);
        if (result instanceof CompletionStage<?> stage) {
            @SuppressWarnings("unchecked")
            CompletionStage<Object> typedStage = (CompletionStage<Object>) stage;
            return typedStage;
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        if (controller == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " has no controller");
        }
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        Object result = invokeController("stream", toInputEvent(inputs), session, streamModes);
        if (result instanceof Iterator<?> iterator) {
            return (Iterator<Object>) iterator;
        }
        if (result instanceof Iterable<?> iterable) {
            return (Iterator<Object>) iterable.iterator();
        }
        return List.of(result).iterator();
    }

    public CompletionStage<Void> releaseSession(String sessionId) {
        if (controller != null && sessionId != null) {
            Object eventQueue = readEventQueue();
            if (eventQueue != null) {
                invokeCompatible(eventQueue, "unsubscribe", getCard().getId(), sessionId);
            }
        }
        return Runner.release(sessionId);
    }

    public CompletionStage<Void> release_session(String sessionId) {
        return releaseSession(sessionId);
    }

    public Object getController() {
        return controller;
    }

    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    public void setController(Object controller) {
        this.controller = controller;
        initializeController();
    }

    private void initializeController() {
        if (controller == null) {
            return;
        }
        invokeCompatible(controller, "init", getCard(), getConfig(), getAbilityManager(), contextEngine);
    }

    private void writeControllerConfig(Object config) {
        if (controller == null) {
            return;
        }
        invokeCompatible(controller, "setConfig", config);
    }

    private Object invokeController(String methodName,
                                    InputEvent inputs,
                                    AgentSessionApi session,
                                    List<StreamMode> streamModes) {
        Object[] args = streamModes == null
                ? new Object[] {inputs, session}
                : new Object[] {inputs, session, streamModes};
        Object result = invokeCompatible(controller, methodName, args);
        if (result != NoMethod.INSTANCE) {
            return result;
        }
        throw new IllegalStateException("controller lacks " + methodName + " method");
    }

    private Object readEventQueue() {
        Object result = invokeCompatible(controller, "getEventQueue");
        return result == NoMethod.INSTANCE ? null : result;
    }

    private InputEvent toInputEvent(Object inputs) {
        return InputEvent.fromUserInput(inputs);
    }

    @SuppressWarnings("unchecked")
    private ControllerConfig normalizeConfig(Object config) {
        if (config == null) {
            Object current = getConfig();
            return current instanceof ControllerConfig existing ? existing : new ControllerConfig();
        }
        if (config instanceof ControllerConfig controllerConfig) {
            return controllerConfig;
        }
        if (config instanceof java.util.Map<?, ?> map) {
            ControllerConfig base = getConfig() instanceof ControllerConfig existing
                    ? copyConfig(existing)
                    : new ControllerConfig();
            applyMap(base, (java.util.Map<?, ?>) map);
            return base;
        }
        throw new IllegalArgumentException("ControllerAgent config must be ControllerConfig, Map, or null");
    }

    private static ControllerConfig copyConfig(ControllerConfig source) {
        ControllerConfig copy = new ControllerConfig();
        copy.setMaxConcurrentTasks(source.getMaxConcurrentTasks());
        copy.setScheduleInterval(source.getScheduleInterval());
        copy.setTaskTimeout(source.getTaskTimeout());
        copy.setDefaultTaskPriority(source.getDefaultTaskPriority());
        copy.setEnableTaskPersistence(source.isEnableTaskPersistence());
        copy.setEventQueueSize(source.getEventQueueSize());
        copy.setEventTimeout(source.getEventTimeout());
        copy.setEnableIntentRecognition(source.isEnableIntentRecognition());
        copy.setIntentLlmId(source.getIntentLlmId());
        copy.setIntentConfidenceThreshold(source.getIntentConfidenceThreshold());
        copy.setIntentTypeList(source.getIntentTypeList());
        copy.setDefaultResponse(copyDefaultResponse(source.getDefaultResponse()));
        copy.setSuppressCompletionSignal(source.isSuppressCompletionSignal());
        copy.setStreamFirstFrameTimeout(source.getStreamFirstFrameTimeout());
        return copy;
    }

    private static ControllerConfig.DefaultResponse copyDefaultResponse(ControllerConfig.DefaultResponse source) {
        if (source == null) {
            return new ControllerConfig.DefaultResponse();
        }
        return new ControllerConfig.DefaultResponse(source.getType(), source.getText());
    }

    private static void applyMap(ControllerConfig config, java.util.Map<?, ?> map) {
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            switch (key) {
                case "max_concurrent_tasks", "maxConcurrentTasks" -> config.setMaxConcurrentTasks(toInt(value));
                case "schedule_interval", "scheduleInterval" -> config.setScheduleInterval(toDouble(value));
                case "task_timeout", "taskTimeout" -> config.setTaskTimeout(toNullableDouble(value));
                case "default_task_priority", "defaultTaskPriority" -> config.setDefaultTaskPriority(toInt(value));
                case "enable_task_persistence", "enableTaskPersistence" ->
                        config.setEnableTaskPersistence(toBoolean(value));
                case "event_queue_size", "eventQueueSize" -> config.setEventQueueSize(toNullableInt(value));
                case "event_timeout", "eventTimeout" -> config.setEventTimeout(toNullableDouble(value));
                case "enable_intent_recognition", "enableIntentRecognition" ->
                        config.setEnableIntentRecognition(toBoolean(value));
                case "intent_llm_id", "intentLlmId" -> config.setIntentLlmId(value == null ? "" : String.valueOf(value));
                case "intent_confidence_threshold", "intentConfidenceThreshold" ->
                        config.setIntentConfidenceThreshold(toDouble(value));
                case "intent_type_list", "intentTypeList" -> config.setIntentTypeList(toStringList(value));
                case "default_response", "defaultResponse" -> config.setDefaultResponse(toDefaultResponse(value));
                case "suppress_completion_signal", "suppressCompletionSignal" ->
                        config.setSuppressCompletionSignal(toBoolean(value));
                case "stream_first_frame_timeout", "streamFirstFrameTimeout" ->
                        config.setStreamFirstFrameTimeout(toNullableDouble(value));
                default -> {
                }
            }
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static Integer toNullableInt(Object value) {
        return value == null ? null : toInt(value);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static Double toNullableDouble(Object value) {
        return value == null ? null : toDouble(value);
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of(String.valueOf(value));
        }
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private static ControllerConfig.DefaultResponse toDefaultResponse(Object value) {
        if (value instanceof ControllerConfig.DefaultResponse defaultResponse) {
            return defaultResponse;
        }
        ControllerConfig.DefaultResponse result = new ControllerConfig.DefaultResponse();
        if (value instanceof java.util.Map<?, ?> map) {
            Object type = map.get("type");
            Object text = map.get("text");
            if (type != null) {
                result.setType(String.valueOf(type));
            }
            if (text != null) {
                result.setText(String.valueOf(text));
            }
        }
        return result;
    }

    private static Object invokeCompatible(Object target, String methodName, Object... args) {
        if (target == null) {
            return NoMethod.INSTANCE;
        }
        Method selected = null;
        int selectedScore = -1;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (method.getParameterCount() != args.length) {
                continue;
            }
            int score = compatibilityScore(method.getParameterTypes(), args);
            if (score > selectedScore) {
                selected = method;
                selectedScore = score;
            }
        }
        if (selected == null) {
            return NoMethod.INSTANCE;
        }
        try {
            return selected.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("controller " + methodName + " is not accessible", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("controller " + methodName + " failed", cause);
        }
    }

    private static int compatibilityScore(Class<?>[] parameterTypes, Object[] args) {
        int score = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            int itemScore = compatibilityScore(parameterTypes[i], args[i]);
            if (itemScore < 0) {
                return -1;
            }
            score += itemScore;
        }
        return score;
    }

    private static int compatibilityScore(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return parameterType.isPrimitive() ? -1 : 1;
        }
        Class<?> boxedParameterType = box(parameterType);
        Class<?> argClass = arg.getClass();
        if (boxedParameterType.equals(argClass)) {
            return 4;
        }
        if (boxedParameterType.isAssignableFrom(argClass)) {
            return boxedParameterType == Object.class ? 1 : 3;
        }
        return -1;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    private enum NoMethod {
        INSTANCE
    }

    private static <T> CompletionStage<T> failed(Throwable throwable) {
        CompletableFuture<T> failed = new CompletableFuture<>();
        failed.completeExceptionally(throwable);
        return failed;
    }
}
