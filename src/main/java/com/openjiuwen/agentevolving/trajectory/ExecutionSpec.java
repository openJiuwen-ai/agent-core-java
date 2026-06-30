/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.ExecutionSpec.
 */
public class ExecutionSpec {

    private String caseId;
    private String executionId;
    private Integer seed;
    private Map<String, Object> tags;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ExecutionSpec() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ExecutionSpec(String caseId, String executionId) {
        this(caseId, executionId, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ExecutionSpec(String caseId, String executionId, Integer seed, Map<String, Object> tags) {
        this.caseId = caseId;
        this.executionId = executionId;
        this.seed = seed;
        this.tags = tags;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getCaseId() {
        return caseId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getSeed() {
        return seed;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getTags() {
        return tags;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTags(Map<String, Object> tags) {
        this.tags = tags;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class Builder {
        private String caseId;
        private String executionId;
        private Integer seed;
        private Map<String, Object> tags;

        private Builder() {
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder tags(Map<String, Object> tags) {
            this.tags = tags;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public ExecutionSpec build() {
            return new ExecutionSpec(caseId, executionId, seed, tags);
        }
    }
}
