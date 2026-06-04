/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime executor for single rollout execution.
 * <p>
 * Mirrors Python's {@code RuntimeExecutor} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.runtime.runtime_executor}.
 */
public class RuntimeExecutor {

    private static final Logger LOGGER = Logger.getLogger(RuntimeExecutor.class.getName());

    private Object agentFactory;
    private Object taskDataFn;
    private Object rewardFn;
    private Object config;

    public RuntimeExecutor() {
    }

    public RuntimeExecutor(Object agentFactory, Object config) {
        this.agentFactory = agentFactory;
        this.config = config;
    }

    public RuntimeExecutor(Object agentFactory, Object taskDataFn, Object rewardFn) {
        this.agentFactory = agentFactory;
        this.taskDataFn = taskDataFn;
        this.rewardFn = rewardFn;
    }

    /**
     * Execute a prompt by wrapping it as an RL task.
     *
     * @param prompt prompt or task payload
     * @return rollout message
     */
    public Object execute(Object prompt) {
        if (prompt instanceof RLTask task) {
            return execute(task);
        }
        return execute(taskFromPrompt(prompt, Map.of()));
    }

    /**
     * Execute a RLTask and return a populated rollout message.
     *
     * @param task RLTask to execute
     * @return rollout message result
     */
    public RolloutMessage execute(RLTask task) {
        RLTask rolloutTask = task != null ? task : new RLTask("default", "default", Map.of(), 0);
        RolloutMessage rolloutMessage = initialMessage(rolloutTask);

        try {
            if (agentFactory == null) {
                throw new IllegalStateException("agent_factory is not set");
            }
            rolloutMessage = executeWithAgent(rolloutTask, rolloutMessage);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE,
                    "RuntimeExecutor error for task " + rolloutTask.getTaskId() + ": " + exception.getMessage(),
                    exception);
            return rolloutMessage;
        }

        if (rewardFn != null) {
            try {
                applyReward(rolloutMessage);
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "Reward computation failed: " + exception.getMessage(), exception);
            }
        }
        rolloutMessage.setEndTime(nowUtc());
        return rolloutMessage;
    }

    /**
     * Java async wrapper matching Python's execute_async coroutine.
     *
     * @param task RLTask to execute
     * @return future rollout message
     */
    public CompletableFuture<RolloutMessage> executeAsync(RLTask task) {
        return CompletableFuture.supplyAsync(() -> execute(task));
    }

    /**
     * Execute with custom parameters.
     *
     * @param prompt prompt value
     * @param params execution parameters
     * @return rollout message
     */
    public Object executeWithParams(Object prompt, Map<String, Object> params) {
        return execute(taskFromPrompt(prompt, params != null ? params : Map.of()));
    }

    private RolloutMessage executeWithAgent(RLTask rlTask, RolloutMessage ignoredInitialMessage) {
        Object agent = invokeCallable(agentFactory, rlTask);
        Map<String, Object> inputs = buildAgentInputs(rlTask);
        RolloutCollector collector = new RolloutCollector(agent);
        Trajectory trajectory = collector.collect(
                agent,
                inputs,
                rlTask.getTaskId(),
                "offline",
                rlTask.getOriginTaskId());

        List<Rollout> rollouts = trajectoryToRollouts(trajectory);
        String now = nowUtc();
        RolloutMessage message = new RolloutMessage();
        message.setTaskId(rlTask.getTaskId());
        message.setOriginTaskId(rlTask.getOriginTaskId());
        message.setRolloutId("rollout-" + safeTaskId(rlTask));
        message.setStartTime(now);
        message.setEndTime(now);
        message.setRolloutInfo(rollouts);
        message.setRewardList(List.of());
        message.setGlobalReward(nullValue());
        message.setTurnCount(rollouts.size());
        message.setRoundNum(rlTask.getRoundNum());

        Object groundTruth = inputs.get("ground_truth");
        if (groundTruth != null && !String.valueOf(groundTruth).isBlank() && !message.getRolloutInfo().isEmpty()) {
            Rollout first = message.getRolloutInfo().get(0);
            Map<String, Object> inputPrompt = first.getInputPrompt() != null
                    ? new LinkedHashMap<>(first.getInputPrompt())
                    : new LinkedHashMap<>();
            inputPrompt.put("ground_truth", groundTruth);
            first.setInputPrompt(inputPrompt);
        }
        return message;
    }

    private Map<String, Object> buildAgentInputs(RLTask rlTask) {
        Map<String, Object> sample = rlTask.getTaskSample() != null
                ? new LinkedHashMap<>(rlTask.getTaskSample())
                : new LinkedHashMap<>();
        if (taskDataFn != null) {
            Object rawInputs = invokeCallable(taskDataFn, sample);
            Map<String, Object> inputs = asStringObjectMap(rawInputs);
            inputs.putIfAbsent("conversation_id", rlTask.getTaskId());
            return inputs;
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", sample.getOrDefault("query", ""));
        inputs.put("ground_truth", sample.getOrDefault("ground_truth", ""));
        inputs.put("conversation_id", rlTask.getTaskId());
        return inputs;
    }

    private void applyReward(RolloutMessage message) {
        Object rawResult = invokeCallable(rewardFn, message);
        Map<String, Object> result = asStringObjectMap(rawResult);
        message.setRewardList(doubleList(result.getOrDefault("reward_list", List.of())));
        message.setGlobalReward(toDouble(result.getOrDefault("global_reward", 0.0d), 0.0d));
    }

    private static RolloutMessage initialMessage(RLTask task) {
        RolloutMessage message = new RolloutMessage();
        message.setRolloutId(UUID.randomUUID().toString());
        message.setTaskId(task.getTaskId());
        message.setOriginTaskId(task.getOriginTaskId());
        message.setStartTime(nowUtc());
        message.setRolloutInfo(List.of());
        message.setRewardList(List.of());
        message.setGlobalReward(0.0d);
        message.setTurnCount(0);
        message.setRoundNum(task.getRoundNum());
        return message;
    }

    private static RLTask taskFromPrompt(Object prompt, Map<String, Object> params) {
        Map<String, Object> sample = new LinkedHashMap<>();
        if (prompt instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sample.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        } else {
            sample.put("query", prompt != null ? String.valueOf(prompt) : "");
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!List.of("task_id", "origin_task_id", "round_num").contains(entry.getKey())) {
                sample.put(entry.getKey(), entry.getValue());
            }
        }
        String taskId = stringValue(params.getOrDefault("task_id", sample.getOrDefault("task_id", UUID.randomUUID())));
        String originTaskId = stringValue(params.getOrDefault(
                "origin_task_id", sample.getOrDefault("origin_task_id", taskId)));
        int roundNum = toInt(params.getOrDefault("round_num", sample.getOrDefault("round_num", 0)), 0);
        return new RLTask(taskId, originTaskId, sample, roundNum);
    }

    private static List<Rollout> trajectoryToRollouts(Object trajectoryObj) {
        if (!(trajectoryObj instanceof Trajectory trajectory) || trajectory.getSteps() == null) {
            return new ArrayList<>();
        }
        List<Rollout> rollouts = new ArrayList<>();
        for (TrajectoryStep step : trajectory.getSteps()) {
            if (!"llm".equals(step.getKind()) || step.getDetail() == null) {
                continue;
            }
            Object detail = step.getDetail();
            Map<String, Object> inputPrompt = new LinkedHashMap<>();
            inputPrompt.put("message", normalizeList(readDetail(detail, "messages")));
            inputPrompt.put("tools", normalizeNullableList(readDetail(detail, "tools")));

            Rollout rollout = new Rollout();
            rollout.setTurnId(rollouts.size());
            rollout.setInputPrompt(inputPrompt);
            rollout.setOutputResponse(normalizeResponse(readDetail(detail, "response")));
            rollout.setLlmConfig(readLlmConfig(step));
            rollout.setInputPromptIds(intList(firstAvailable(
                    readStepValue(step, "promptTokenIds", "prompt_token_ids", "inputPromptIds"),
                    readNestedMeta(detail, "prompt_token_ids"))));
            rollout.setOutputResponseIds(intList(firstAvailable(
                    readStepValue(step, "completionTokenIds", "completion_token_ids", "outputResponseIds"),
                    readNestedMeta(detail, "completion_token_ids"))));
            rollouts.add(rollout);
        }
        return rollouts;
    }

    private static Object readDetail(Object detail, String fieldName) {
        if (detail instanceof LLMCallDetail llmDetail) {
            return switch (fieldName) {
                case "messages" -> llmDetail.getMessages();
                case "tools" -> llmDetail.getTools();
                case "response" -> llmDetail.getResponse();
                default -> nullValue();
            };
        }
        return readProperty(detail, fieldName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readLlmConfig(TrajectoryStep step) {
        if (step.getMeta() != null && step.getMeta().get("llm_config") instanceof Map<?, ?> map) {
            return asStringObjectMap(map);
        }
        return nullValue();
    }

    private static Object readNestedMeta(Object detail, String key) {
        Object meta = readProperty(detail, "meta");
        if (meta instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return nullValue();
    }

    private static Object readStepValue(TrajectoryStep step, String... names) {
        for (String name : names) {
            Object value = readProperty(step, name);
            if (value != Missing.VALUE) {
                return value;
            }
        }
        return nullValue();
    }

    private static Object firstAvailable(Object first, Object second) {
        return first != null && first != Missing.VALUE ? first : second;
    }

    private static List<Object> normalizeList(Object value) {
        List<Object> result = normalizeNullableList(value);
        return result != null ? result : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> normalizeNullableList(Object value) {
        if (value == null || value == Missing.VALUE) {
            return nullValue();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>((List<Object>) list);
        }
        if (value instanceof Object[] array) {
            List<Object> result = new ArrayList<>();
            Collections.addAll(result, array);
            return result;
        }
        return new ArrayList<>(List.of(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeResponse(Object value) {
        if (value == null || value == Missing.VALUE) {
            return nullValue();
        }
        if (value instanceof Map<?, ?> map) {
            return asStringObjectMap(map);
        }
        Object dumped = invokeIfPresent(value, "modelDump");
        if (dumped == Missing.VALUE) {
            dumped = invokeIfPresent(value, "model_dump");
        }
        if (dumped instanceof Map<?, ?> map) {
            return asStringObjectMap(map);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("role", stringValue(firstAvailable(readProperty(value, "role"), "assistant")));
        response.put("content", stringValue(firstAvailable(readProperty(value, "content"), "")));
        return response;
    }

    private static Object invokeCallable(Object callable, Object argument) {
        if (callable instanceof Function<?, ?> function) {
            @SuppressWarnings("unchecked")
            Function<Object, Object> typed = (Function<Object, Object>) function;
            return unwrapFuture(typed.apply(argument));
        }
        for (String methodName : List.of("apply", "call", "__call__", "createAgent")) {
            Object result = invokeIfPresent(callable, methodName, argument);
            if (result != Missing.VALUE) {
                return unwrapFuture(result);
            }
        }
        throw new IllegalStateException("Object is not callable: " + callable.getClass().getName());
    }

    private static Object unwrapFuture(Object value) {
        if (value instanceof CompletableFuture<?> future) {
            return future.join();
        }
        return value;
    }

    private static Object invokeIfPresent(Object target, String name, Object... args) {
        if (target == null) {
            return Missing.VALUE;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                    continue;
                }
                if (!parametersCompatible(method.getParameterTypes(), args)) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Failed to invoke " + name, exception);
                }
            }
            type = type.getSuperclass();
        }
        return Missing.VALUE;
    }

    private static Object readProperty(Object target, String name) {
        if (target == null) {
            return Missing.VALUE;
        }
        if (target instanceof Map<?, ?> map && map.containsKey(name)) {
            return map.get(name);
        }
        String camel = toCamel(name);
        for (String methodName : List.of(name, "get" + capitalize(camel), "is" + capitalize(camel))) {
            Object value = invokeIfPresent(target, methodName);
            if (value != Missing.VALUE) {
                return value;
            }
        }
        return Missing.VALUE;
    }

    private static boolean parametersCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null || args[i] == Missing.VALUE) {
                continue;
            }
            if (!wrap(parameterTypes[i]).isAssignableFrom(args[i].getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
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
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static Map<String, Object> asStringObjectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static List<Double> doubleList(Object value) {
        List<Double> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                result.add(toDouble(item, 0.0d));
            }
        }
        return result;
    }

    private static List<Integer> intList(Object value) {
        if (value == null || value == Missing.VALUE) {
            return nullValue();
        }
        List<Integer> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                result.add(toInt(item, 0));
            }
            return result;
        }
        if (value instanceof Object[] array) {
            for (Object item : array) {
                result.add(toInt(item, 0));
            }
            return result;
        }
        return nullValue();
    }

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String safeTaskId(RLTask task) {
        return task.getTaskId() != null ? task.getTaskId() : "default";
    }

    private static String stringValue(Object value) {
        return value != null && value != Missing.VALUE ? String.valueOf(value) : "";
    }

    private static String nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private static String toCamel(String value) {
        if (value == null || !value.contains("_")) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char c : value.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    @SuppressWarnings("unchecked")
    private static <T> T nullValue() {
        return (T) null;
    }

    public Object getAgentFactory() { return agentFactory; }
    public void setAgentFactory(Object agentFactory) { this.agentFactory = agentFactory; }
    public Object getTaskDataFn() { return taskDataFn; }
    public void setTaskDataFn(Object taskDataFn) { this.taskDataFn = taskDataFn; }
    public Object getRewardFn() { return rewardFn; }
    public void setRewardFn(Object rewardFn) { this.rewardFn = rewardFn; }
    public Object getConfig() { return config; }
    public void setConfig(Object config) { this.config = config; }

    private enum Missing {
        VALUE
    }
}
