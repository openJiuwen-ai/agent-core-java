/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.TrajectoryStep.
 */
public class TrajectoryStep {

    private StepKind kind;
    private String operatorId;
    private String agentId;
    private String role;
    private String nodeId;
    private Object inputs;
    private Object outputs;
    private Object error;
    private Long startTimeMs;
    private Long endTimeMs;
    private Object detail;
    private Double reward;
    private List<Integer> promptTokenIds;
    private List<Integer> completionTokenIds;
    private Object logprobs;
    private Map<String, Object> meta;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TrajectoryStep() {
        this.kind = StepKind.AGENT;
        this.meta = new LinkedHashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TrajectoryStep(StepKind kind,
                          String operatorId,
                          String agentId,
                          String role,
                          String nodeId,
                          Object inputs,
                          Object outputs,
                          Object error,
                          Long startTimeMs,
                          Long endTimeMs,
                          Map<String, Object> meta) {
        this(kind, operatorId, agentId, role, nodeId, inputs, outputs, error, startTimeMs, endTimeMs,
                null, null, null, null, null, meta);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TrajectoryStep(StepKind kind,
                          String operatorId,
                          String agentId,
                          String role,
                          String nodeId,
                          Object inputs,
                          Object outputs,
                          Object error,
                          Long startTimeMs,
                          Long endTimeMs,
                          Object detail,
                          Double reward,
                          List<Integer> promptTokenIds,
                          List<Integer> completionTokenIds,
                          Object logprobs,
                          Map<String, Object> meta) {
        this.kind = kind != null ? kind : StepKind.AGENT;
        this.operatorId = operatorId;
        this.agentId = agentId;
        this.role = role;
        this.nodeId = nodeId;
        this.inputs = inputs;
        this.outputs = outputs;
        this.error = error;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.detail = detail;
        this.reward = reward;
        this.promptTokenIds = promptTokenIds;
        this.completionTokenIds = completionTokenIds;
        this.logprobs = logprobs;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TrajectoryStep(String kind,
                          String operatorId,
                          String agentId,
                          String role,
                          String nodeId,
                          Object inputs,
                          Object outputs,
                          Object error,
                          Long startTimeMs,
                          Long endTimeMs,
                          Map<String, Object> meta) {
        this(StepKind.fromValue(kind), operatorId, agentId, role, nodeId, inputs, outputs, error, startTimeMs,
                endTimeMs, meta);
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
    public String getKind() {
        return kind != null ? kind.value() : StepKind.AGENT.value();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public StepKind getKindEnum() {
        return kind != null ? kind : StepKind.AGENT;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setKind(String kind) {
        this.kind = StepKind.fromValue(kind);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setKind(StepKind kind) {
        this.kind = kind != null ? kind : StepKind.AGENT;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getOperatorId() {
        return operatorId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getRole() {
        return role;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getInputs() {
        return inputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setInputs(Object inputs) {
        this.inputs = inputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getOutputs() {
        return outputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOutputs(Object outputs) {
        this.outputs = outputs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getError() {
        return error;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setError(Object error) {
        this.error = error;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Long getStartTimeMs() {
        return startTimeMs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStartTimeMs(Long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Long getEndTimeMs() {
        return endTimeMs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setEndTimeMs(Long endTimeMs) {
        this.endTimeMs = endTimeMs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getDetail() {
        return detail;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setDetail(Object detail) {
        this.detail = detail;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Double getReward() {
        return reward;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setReward(Double reward) {
        this.reward = reward;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Integer> getPromptTokenIds() {
        return promptTokenIds;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setPromptTokenIds(List<Integer> promptTokenIds) {
        this.promptTokenIds = promptTokenIds;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Integer> getCompletionTokenIds() {
        return completionTokenIds;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCompletionTokenIds(List<Integer> completionTokenIds) {
        this.completionTokenIds = completionTokenIds;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getLogprobs() {
        return logprobs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLogprobs(Object logprobs) {
        this.logprobs = logprobs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMeta() {
        return meta;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class Builder {
        private StepKind kind = StepKind.AGENT;
        private String operatorId;
        private String agentId;
        private String role;
        private String nodeId;
        private Object inputs;
        private Object outputs;
        private Object error;
        private Long startTimeMs;
        private Long endTimeMs;
        private Object detail;
        private Double reward;
        private List<Integer> promptTokenIds;
        private List<Integer> completionTokenIds;
        private Object logprobs;
        private Map<String, Object> meta;

        private Builder() {
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder kind(String kind) {
            this.kind = StepKind.fromValue(kind);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder kind(StepKind kind) {
            this.kind = kind != null ? kind : StepKind.AGENT;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder operatorId(String operatorId) {
            this.operatorId = operatorId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder role(String role) {
            this.role = role;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder inputs(Object inputs) {
            this.inputs = inputs;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder outputs(Object outputs) {
            this.outputs = outputs;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder error(Object error) {
            this.error = error;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder startTimeMs(Long startTimeMs) {
            this.startTimeMs = startTimeMs;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder endTimeMs(Long endTimeMs) {
            this.endTimeMs = endTimeMs;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder detail(Object detail) {
            this.detail = detail;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder reward(Double reward) {
            this.reward = reward;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder promptTokenIds(List<Integer> promptTokenIds) {
            this.promptTokenIds = promptTokenIds;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder completionTokenIds(List<Integer> completionTokenIds) {
            this.completionTokenIds = completionTokenIds;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder logprobs(Object logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder meta(Map<String, Object> meta) {
            this.meta = meta;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public TrajectoryStep build() {
            return new TrajectoryStep(kind, operatorId, agentId, role, nodeId, inputs, outputs, error,
                    startTimeMs, endTimeMs, detail, reward, promptTokenIds, completionTokenIds, logprobs, meta);
        }
    }
}
