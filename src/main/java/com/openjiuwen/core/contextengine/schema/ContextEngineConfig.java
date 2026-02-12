// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.contextengine.schema;

/**
 * Configuration for the context engine.
 *
 * 对应 Python: agent-core/openjiuwen/core/context_engine/schema/config.py - ContextEngineConfig
 */
public class ContextEngineConfig {
    
    /**
     * Hard upper limit on the total number of messages allowed in any context.
     * If null, no hard limit is enforced.
     */
    private final Integer maxContextMessageNum;
    
    /**
     * Number of most-recent messages to retain when a sliding window is created
     * without an explicit token or message count.
     */
    private final Integer defaultWindowMessageNum;
    
    /**
     * Maximum token budget for a sliding window when token-based rather than
     * message-based truncation is requested. If null, truncation falls back to
     * defaultWindowMessageNum.
     */
    private final Integer defaultWindowTokenNum;
    
    /**
     * Number of messages to load from memory.
     * Default is 20.
     */
    private final int memoryMessageNum;
    
    private ContextEngineConfig(Builder builder) {
        this.maxContextMessageNum = builder.maxContextMessageNum;
        this.defaultWindowMessageNum = builder.defaultWindowMessageNum;
        this.defaultWindowTokenNum = builder.defaultWindowTokenNum;
        this.memoryMessageNum = builder.memoryMessageNum;
    }
    
    /**
     * Creates a new builder with default values.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a default configuration with memoryMessageNum=20.
     *
     * @return default ContextEngineConfig
     */
    public static ContextEngineConfig defaults() {
        return new Builder().build();
    }
    
    public Integer getMaxContextMessageNum() {
        return maxContextMessageNum;
    }
    
    public Integer getDefaultWindowMessageNum() {
        return defaultWindowMessageNum;
    }
    
    public Integer getDefaultWindowTokenNum() {
        return defaultWindowTokenNum;
    }
    
    public int getMemoryMessageNum() {
        return memoryMessageNum;
    }
    
    /**
     * Builder for ContextEngineConfig.
     */
    public static class Builder {
        private Integer maxContextMessageNum = null;
        private Integer defaultWindowMessageNum = null;
        private Integer defaultWindowTokenNum = null;
        private int memoryMessageNum = 20;
        
        public Builder maxContextMessageNum(Integer maxContextMessageNum) {
            if (maxContextMessageNum != null && maxContextMessageNum <= 0) {
                throw new IllegalArgumentException("maxContextMessageNum must be greater than 0");
            }
            this.maxContextMessageNum = maxContextMessageNum;
            return this;
        }
        
        public Builder defaultWindowMessageNum(Integer defaultWindowMessageNum) {
            if (defaultWindowMessageNum != null && defaultWindowMessageNum <= 0) {
                throw new IllegalArgumentException("defaultWindowMessageNum must be greater than 0");
            }
            this.defaultWindowMessageNum = defaultWindowMessageNum;
            return this;
        }
        
        public Builder defaultWindowTokenNum(Integer defaultWindowTokenNum) {
            if (defaultWindowTokenNum != null && defaultWindowTokenNum <= 0) {
                throw new IllegalArgumentException("defaultWindowTokenNum must be greater than 0");
            }
            this.defaultWindowTokenNum = defaultWindowTokenNum;
            return this;
        }
        
        public Builder memoryMessageNum(int memoryMessageNum) {
            if (memoryMessageNum <= 0) {
                throw new IllegalArgumentException("memoryMessageNum must be greater than 0");
            }
            this.memoryMessageNum = memoryMessageNum;
            return this;
        }
        
        public ContextEngineConfig build() {
            return new ContextEngineConfig(this);
        }
    }
}

