// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl;

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
}