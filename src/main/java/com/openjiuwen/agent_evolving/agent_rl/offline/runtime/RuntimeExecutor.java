/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.RlSchemas;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Self-contained single-task rollout executor.
 *
 * <p>Mirrors Python's {@code RuntimeExecutor} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/runtime/runtime_executor.py}.</p>
 */
public class RuntimeExecutor {

    private static final Logger LOGGER = Logger.getLogger(RuntimeExecutor.class.getName());

    private Function<RLTask, ?> agentFactory;
    private Function<Map<String, Object>, Map<String, Object>> taskDataFn;
    private Function<RolloutMessage, Map<String, Object>> rewardFn;

    public RuntimeExecutor() {
    }

    public RuntimeExecutor(Function<RLTask, ?> agentFactory,
                           Function<Map<String, Object>, Map<String, Object>> taskDataFn,
                           Function<RolloutMessage, Map<String, Object>> rewardFn) {
        this.agentFactory = agentFactory;
        this.taskDataFn = taskDataFn;
        this.rewardFn = rewardFn;
    }

    public void setAgentFactory(Function<RLTask, ?> factory) {
        this.agentFactory = factory;
    }

    public void setTaskDataFn(Function<Map<String, Object>, Map<String, Object>> fn) {
        this.taskDataFn = fn;
    }

    public void setRewardFn(Function<RolloutMessage, Map<String, Object>> fn) {
        this.rewardFn = fn;
    }

    public CompletionStage<RolloutMessage> executeAsync(RLTask rolloutTask) {
        return CompletableFuture.supplyAsync(() -> execute(rolloutTask));
    }

    public RolloutMessage execute(RLTask rolloutTask) {
        RLTask task = rolloutTask != null ? rolloutTask : new RLTask();
        String startTime = nowUtc();
        RolloutMessage rolloutMessage = initialMessage(task, startTime);

        try {
            if (agentFactory == null) {
                throw new IllegalStateException("agent_factory is not set");
            }
            rolloutMessage = executeWithAgent(task);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE,
                    "RuntimeExecutor error for task " + task.getTaskId() + ": " + exception.getMessage(),
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

    private RolloutMessage executeWithAgent(RLTask rlTask) {
        Object agent = agentFactory.apply(rlTask);
        Map<String, Object> inputs = buildAgentInputs(rlTask);
        TrajectoryCollector collector = new TrajectoryCollector();
        Trajectory trajectory = collector.collect(
                agent,
                inputs,
                rlTask.getTaskId(),
                "offline",
                rlTask.getOriginTaskId()
        ).toCompletableFuture().join();
        List<Rollout> rollouts = RlSchemas.trajectoryToRollouts(trajectory);

        String now = nowUtc();
        RolloutMessage message = new RolloutMessage();
        message.setTaskId(rlTask.getTaskId());
        message.setOriginTaskId(rlTask.getOriginTaskId());
        message.setRolloutId("rollout-" + safeTaskId(rlTask));
        message.setStartTime(now);
        message.setEndTime(now);
        message.setRolloutInfo(rollouts);
        message.setRewardList(List.of());
        message.setGlobalReward(null);
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
            Map<String, Object> inputs = taskDataFn.apply(new LinkedHashMap<>(sample));
            Map<String, Object> effectiveInputs = inputs != null ? new LinkedHashMap<>(inputs) : new LinkedHashMap<>();
            effectiveInputs.putIfAbsent("conversation_id", rlTask.getTaskId());
            return effectiveInputs;
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", sample.getOrDefault("query", ""));
        inputs.put("ground_truth", sample.getOrDefault("ground_truth", ""));
        inputs.put("conversation_id", rlTask.getTaskId());
        return inputs;
    }

    private void applyReward(RolloutMessage message) {
        Map<String, Object> result = rewardFn.apply(message);
        Map<String, Object> rewardResult = result != null ? result : Map.of();
        message.setRewardList(doubleList(rewardResult.getOrDefault("reward_list", List.of())));
        message.setGlobalReward(toDouble(rewardResult.getOrDefault("global_reward", 0.0d), 0.0d));
    }

    private static RolloutMessage initialMessage(RLTask task, String startTime) {
        RolloutMessage message = new RolloutMessage();
        message.setRolloutId(UUID.randomUUID().toString());
        message.setTaskId(task.getTaskId());
        message.setOriginTaskId(task.getOriginTaskId());
        message.setStartTime(startTime);
        message.setRolloutInfo(List.of());
        message.setRewardList(List.of());
        message.setGlobalReward(0.0d);
        message.setTurnCount(0);
        message.setRoundNum(task.getRoundNum());
        return message;
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

    private static String nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    public Function<RLTask, ?> getAgentFactory() {
        return agentFactory;
    }

    public Function<Map<String, Object>, Map<String, Object>> getTaskDataFn() {
        return taskDataFn;
    }

    public Function<RolloutMessage, Map<String, Object>> getRewardFn() {
        return rewardFn;
    }
}
