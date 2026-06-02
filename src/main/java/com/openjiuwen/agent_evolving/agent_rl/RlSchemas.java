// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl;

import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;

import java.util.*;

/**
 * RL training schemas and data structures.
 * <p>
 * Mirrors Python's {@code schemas.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.schemas}.
 */
public final class RlSchemas {
    
    private RlSchemas() {
        // Utility class
    }
    
    /**
     * Training sample schema.
     */
    public static class TrainingSample {
        private final String trajectoryId;
        private final int stepIndex;
        private final List<Integer> tokenIds;
        private final List<Double> logprobs;
        private final Double reward;
        
        public TrainingSample(String trajectoryId, int stepIndex,
                              List<Integer> tokenIds, List<Double> logprobs, Double reward) {
            this.trajectoryId = trajectoryId;
            this.stepIndex = stepIndex;
            this.tokenIds = tokenIds;
            this.logprobs = logprobs;
            this.reward = reward;
        }
        
        public String getTrajectoryId() { return trajectoryId; }
        public int getStepIndex() { return stepIndex; }
        public List<Integer> getTokenIds() { return tokenIds; }
        public List<Double> getLogprobs() { return logprobs; }
        public Double getReward() { return reward; }
    }
    
    /**
     * Training batch schema.
     */
    public static class TrainingBatch {
        private final List<TrainingSample> samples;
        private final String modelId;
        private final Map<String, Object> metadata;
        
        public TrainingBatch(List<TrainingSample> samples, String modelId, Map<String, Object> metadata) {
            this.samples = samples != null ? samples : new ArrayList<>();
            this.modelId = modelId;
            this.metadata = metadata != null ? metadata : new LinkedHashMap<>();
        }
        
        public List<TrainingSample> getSamples() { return samples; }
        public String getModelId() { return modelId; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    /**
     * Convert LLM trajectory steps into rollout objects.
     *
     * <p>Mirrors Python's {@code trajectory_to_rollouts} in
     * {@code openjiuwen.agent_evolving.agent_rl.schemas}.</p>
     *
     * @param trajectory source trajectory
     * @return one rollout per LLM step
     */
    public static List<Rollout> trajectoryToRollouts(Trajectory trajectory) {
        if (trajectory == null || trajectory.getSteps() == null) {
            return List.of();
        }
        List<Rollout> rollouts = new ArrayList<>();
        int turnId = 0;
        for (TrajectoryStep step : trajectory.getSteps()) {
            if (step == null || !"llm".equals(step.getKind())) {
                continue;
            }
            Object detailObject = step.getDetail();
            if (!(detailObject instanceof LLMCallDetail detail)) {
                continue;
            }

            Rollout rollout = new Rollout();
            rollout.setTurnId(turnId++);

            Map<String, Object> inputPrompt = new LinkedHashMap<>();
            inputPrompt.put("message", normalizeMessages(detail.getMessages()));
            inputPrompt.put("tools", detail.getTools() != null ? new ArrayList<>(detail.getTools()) : null);
            rollout.setInputPrompt(inputPrompt);
            rollout.setOutputResponse(normalizeMessage(detail.getResponse()));
            rollout.setLlmConfig(Map.of("model", detail.getModel()));
            rollout.setInputPromptIds(firstIntegerList(step.getPromptTokenIds(), detail.getMeta().get("prompt_token_ids")));
            rollout.setOutputResponseIds(firstIntegerList(step.getCompletionTokenIds(), detail.getMeta().get("completion_token_ids")));
            rollouts.add(rollout);
        }
        return rollouts;
    }

    private static List<Map<String, Object>> normalizeMessages(List<Map<String, Object>> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            normalized.add(normalizeMessage(message));
        }
        return normalized;
    }

    private static Map<String, Object> normalizeMessage(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return normalized;
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("role", "assistant");
        fallback.put("content", value == null ? "" : String.valueOf(value));
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> firstIntegerList(Object first, Object second) {
        Object value = first != null ? first : second;
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Integer> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                out.add(number.intValue());
            }
        }
        return out;
    }
}
