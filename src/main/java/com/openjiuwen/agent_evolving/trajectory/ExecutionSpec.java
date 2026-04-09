  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.ExecutionSpec.
 */
public class ExecutionSpec {

    private String caseId;
    private String executionId;
    private Integer seed;
    private Map<String, Object> tags;

    public ExecutionSpec() {
    }

    public ExecutionSpec(String caseId, String executionId) {
        this(caseId, executionId, null, null);
    }

    public ExecutionSpec(String caseId, String executionId, Integer seed, Map<String, Object> tags) {
        this.caseId = caseId;
        this.executionId = executionId;
        this.seed = seed;
        this.tags = tags;
    }

    public static Builder builder() { return new Builder(); }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public Integer getSeed() { return seed; }
    public void setSeed(Integer seed) { this.seed = seed; }
    public Map<String, Object> getTags() { return tags; }
    public void setTags(Map<String, Object> tags) { this.tags = tags; }

    public static final class Builder {
        private String caseId;
        private String executionId;
        private Integer seed;
        private Map<String, Object> tags;

        private Builder() {
        }

        public Builder caseId(String caseId) { this.caseId = caseId; return this; }
        public Builder executionId(String executionId) { this.executionId = executionId; return this; }
        public Builder seed(Integer seed) { this.seed = seed; return this; }
        public Builder tags(Map<String, Object> tags) { this.tags = tags; return this; }

        public ExecutionSpec build() {
            return new ExecutionSpec(caseId, executionId, seed, tags);
        }
    }
}
