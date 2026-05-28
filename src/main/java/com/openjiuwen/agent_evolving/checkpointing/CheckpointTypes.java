// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.checkpointing;

import java.util.*;

/**
 * Checkpoint types and schemas.
 * <p>
 * Mirrors Python's {@code types.py} from
 * {@code openjiuwen.agent_evolving.checkpointing.types}.
 */
public final class CheckpointTypes {
    
    private CheckpointTypes() {
        // Utility class
    }
    
    /**
     * Evolve checkpoint data structure.
     */
    public static class EvolveCheckpoint {
        private final String checkpointId;
        private final String modelId;
        private final String operatorId;
        private final double timestamp;
        private final Map<String, Object> state;
        private final Map<String, Object> metadata;
        
        public EvolveCheckpoint(String checkpointId, String modelId, String operatorId,
                               double timestamp, Map<String, Object> state, Map<String, Object> metadata) {
            this.checkpointId = checkpointId;
            this.modelId = modelId;
            this.operatorId = operatorId;
            this.timestamp = timestamp;
            this.state = state != null ? state : new LinkedHashMap<>();
            this.metadata = metadata != null ? metadata : new LinkedHashMap<>();
        }
        
        public String getCheckpointId() { return checkpointId; }
        public String getModelId() { return modelId; }
        public String getOperatorId() { return operatorId; }
        public double getTimestamp() { return timestamp; }
        public Map<String, Object> getState() { return state; }
        public Map<String, Object> getMetadata() { return metadata; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static final class Builder {
            private String checkpointId;
            private String modelId;
            private String operatorId;
            private double timestamp = System.currentTimeMillis() / 1000.0;
            private Map<String, Object> state = new LinkedHashMap<>();
            private Map<String, Object> metadata = new LinkedHashMap<>();
            
            public Builder checkpointId(String checkpointId) { this.checkpointId = checkpointId; return this; }
            public Builder modelId(String modelId) { this.modelId = modelId; return this; }
            public Builder operatorId(String operatorId) { this.operatorId = operatorId; return this; }
            public Builder timestamp(double timestamp) { this.timestamp = timestamp; return this; }
            public Builder state(Map<String, Object> state) { this.state = state; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
            
            public EvolveCheckpoint build() {
                return new EvolveCheckpoint(checkpointId, modelId, operatorId, timestamp, state, metadata);
            }
        }
    }
}