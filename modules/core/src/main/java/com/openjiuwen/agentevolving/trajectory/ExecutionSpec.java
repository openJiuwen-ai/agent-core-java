/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.ExecutionSpec.
 * 
 * @since 0.1.7
 */
public class ExecutionSpec {
    private String caseId;
    private String executionId;
    private Integer seed;
    private Map<String, Object> tags;

    /**
     * ExecutionSpec.
     * 
     * @since 0.1.7
     */
    public ExecutionSpec() {
    }

    /**
     * ExecutionSpec.
     * 
     * @param caseId caseId
     * @param executionId executionId
     * @since 0.1.7
     */
    public ExecutionSpec(String caseId, String executionId) {
        this(caseId, executionId, null, null);
    }

    /**
     * ExecutionSpec.
     * 
     * @param caseId caseId
     * @param executionId executionId
     * @param seed seed
     * @param tags tags
     * @since 0.1.7
     */
    public ExecutionSpec(String caseId, String executionId, Integer seed, Map<String, Object> tags) {
        this.caseId = caseId;
        this.executionId = executionId;
        this.seed = seed;
        this.tags = tags;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * getCaseId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getCaseId() {
        return caseId;
    }

    /**
     * setCaseId.
     * 
     * @param caseId caseId
     * @since 0.1.7
     */
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    /**
     * getExecutionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * setExecutionId.
     * 
     * @param executionId executionId
     * @since 0.1.7
     */
    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    /**
     * getSeed.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getSeed() {
        return seed;
    }

    /**
     * setSeed.
     * 
     * @param seed seed
     * @since 0.1.7
     */
    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    /**
     * getTags.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getTags() {
        return tags;
    }

    /**
     * setTags.
     * 
     * @param tags tags
     * @since 0.1.7
     */
    public void setTags(Map<String, Object> tags) {
        this.tags = tags;
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static final class Builder {
        private String caseId;
        private String executionId;
        private Integer seed;
        private Map<String, Object> tags;

        /**
         * Builder.
         * 
         * @since 0.1.7
         */
        private Builder() {
        }

        /**
         * caseId.
         * 
         * @param caseId caseId
         * @return the result
         * @since 0.1.7
         */
        public Builder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        /**
         * executionId.
         * 
         * @param executionId executionId
         * @return the result
         * @since 0.1.7
         */
        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        /**
         * seed.
         * 
         * @param seed seed
         * @return the result
         * @since 0.1.7
         */
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * tags.
         * 
         * @param tags tags
         * @return the result
         * @since 0.1.7
         */
        public Builder tags(Map<String, Object> tags) {
            this.tags = tags;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public ExecutionSpec build() {
            return new ExecutionSpec(caseId, executionId, seed, tags);
        }
    }
}
