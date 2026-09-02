/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.TrajectoryStep.
 * 
 * @since 0.1.7
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
     * TrajectoryStep.
     * 
     * @since 0.1.7
     */
    public TrajectoryStep() {
        this.kind = StepKind.AGENT;
        this.meta = new LinkedHashMap<>();
    }

    /**
     * TrajectoryStep.
     * 
     * @param kind kind
     * @param operatorId operatorId
     * @param agentId agentId
     * @param role role
     * @param nodeId nodeId
     * @param inputs inputs
     * @param outputs outputs
     * @param error error
     * @param startTimeMs startTimeMs
     * @param endTimeMs endTimeMs
     * @param meta meta
     * @since 0.1.7
     */
    public TrajectoryStep(StepKind kind, String operatorId, String agentId, String role, String nodeId, Object inputs,
            Object outputs, Object error, Long startTimeMs, Long endTimeMs, Map<String, Object> meta) {
        this(kind, operatorId, agentId, role, nodeId, inputs, outputs, error, startTimeMs, endTimeMs, null, null, null,
                null, null, meta);
    }

    /**
     * TrajectoryStep.
     * 
     * @param kind kind
     * @param operatorId operatorId
     * @param agentId agentId
     * @param role role
     * @param nodeId nodeId
     * @param inputs inputs
     * @param outputs outputs
     * @param error error
     * @param startTimeMs startTimeMs
     * @param endTimeMs endTimeMs
     * @param detail detail
     * @param reward reward
     * @param promptTokenIds promptTokenIds
     * @param completionTokenIds completionTokenIds
     * @param logprobs logprobs
     * @param meta meta
     * @since 0.1.7
     */
    public TrajectoryStep(StepKind kind, String operatorId, String agentId, String role, String nodeId, Object inputs,
            Object outputs, Object error, Long startTimeMs, Long endTimeMs, Object detail, Double reward,
            List<Integer> promptTokenIds, List<Integer> completionTokenIds, Object logprobs, Map<String, Object> meta) {
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
     * TrajectoryStep.
     * 
     * @param kind kind
     * @param operatorId operatorId
     * @param agentId agentId
     * @param role role
     * @param nodeId nodeId
     * @param inputs inputs
     * @param outputs outputs
     * @param error error
     * @param startTimeMs startTimeMs
     * @param endTimeMs endTimeMs
     * @param meta meta
     * @since 0.1.7
     */
    public TrajectoryStep(String kind, String operatorId, String agentId, String role, String nodeId, Object inputs,
            Object outputs, Object error, Long startTimeMs, Long endTimeMs, Map<String, Object> meta) {
        this(StepKind.fromValue(kind), operatorId, agentId, role, nodeId, inputs, outputs, error, startTimeMs,
                endTimeMs, meta);
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
     * getKind.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getKind() {
        return kind != null ? kind.value() : StepKind.AGENT.value();
    }

    /**
     * getKindEnum.
     * 
     * @return the result
     * @since 0.1.7
     */
    public StepKind getKindEnum() {
        return kind != null ? kind : StepKind.AGENT;
    }

    /**
     * setKind.
     * 
     * @param kind kind
     * @since 0.1.7
     */
    public void setKind(String kind) {
        this.kind = StepKind.fromValue(kind);
    }

    /**
     * setKind.
     * 
     * @param kind kind
     * @since 0.1.7
     */
    public void setKind(StepKind kind) {
        this.kind = kind != null ? kind : StepKind.AGENT;
    }

    /**
     * getOperatorId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getOperatorId() {
        return operatorId;
    }

    /**
     * setOperatorId.
     * 
     * @param operatorId operatorId
     * @since 0.1.7
     */
    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    /**
     * getAgentId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * setAgentId.
     * 
     * @param agentId agentId
     * @since 0.1.7
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /**
     * getRole.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getRole() {
        return role;
    }

    /**
     * setRole.
     * 
     * @param role role
     * @since 0.1.7
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * getNodeId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * setNodeId.
     * 
     * @param nodeId nodeId
     * @since 0.1.7
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * getInputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getInputs() {
        return inputs;
    }

    /**
     * setInputs.
     * 
     * @param inputs inputs
     * @since 0.1.7
     */
    public void setInputs(Object inputs) {
        this.inputs = inputs;
    }

    /**
     * getOutputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getOutputs() {
        return outputs;
    }

    /**
     * setOutputs.
     * 
     * @param outputs outputs
     * @since 0.1.7
     */
    public void setOutputs(Object outputs) {
        this.outputs = outputs;
    }

    /**
     * getError.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getError() {
        return error;
    }

    /**
     * setError.
     * 
     * @param error error
     * @since 0.1.7
     */
    public void setError(Object error) {
        this.error = error;
    }

    /**
     * getStartTimeMs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Long getStartTimeMs() {
        return startTimeMs;
    }

    /**
     * setStartTimeMs.
     * 
     * @param startTimeMs startTimeMs
     * @since 0.1.7
     */
    public void setStartTimeMs(Long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    /**
     * getEndTimeMs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Long getEndTimeMs() {
        return endTimeMs;
    }

    /**
     * setEndTimeMs.
     * 
     * @param endTimeMs endTimeMs
     * @since 0.1.7
     */
    public void setEndTimeMs(Long endTimeMs) {
        this.endTimeMs = endTimeMs;
    }

    /**
     * getDetail.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getDetail() {
        return detail;
    }

    /**
     * setDetail.
     * 
     * @param detail detail
     * @since 0.1.7
     */
    public void setDetail(Object detail) {
        this.detail = detail;
    }

    /**
     * getReward.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getReward() {
        return reward;
    }

    /**
     * setReward.
     * 
     * @param reward reward
     * @since 0.1.7
     */
    public void setReward(Double reward) {
        this.reward = reward;
    }

    /**
     * getPromptTokenIds.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Integer> getPromptTokenIds() {
        return promptTokenIds;
    }

    /**
     * setPromptTokenIds.
     * 
     * @param promptTokenIds promptTokenIds
     * @since 0.1.7
     */
    public void setPromptTokenIds(List<Integer> promptTokenIds) {
        this.promptTokenIds = promptTokenIds;
    }

    /**
     * getCompletionTokenIds.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Integer> getCompletionTokenIds() {
        return completionTokenIds;
    }

    /**
     * setCompletionTokenIds.
     * 
     * @param completionTokenIds completionTokenIds
     * @since 0.1.7
     */
    public void setCompletionTokenIds(List<Integer> completionTokenIds) {
        this.completionTokenIds = completionTokenIds;
    }

    /**
     * getLogprobs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getLogprobs() {
        return logprobs;
    }

    /**
     * setLogprobs.
     * 
     * @param logprobs logprobs
     * @since 0.1.7
     */
    public void setLogprobs(Object logprobs) {
        this.logprobs = logprobs;
    }

    /**
     * getMeta.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMeta() {
        return meta;
    }

    /**
     * setMeta.
     * 
     * @param meta meta
     * @since 0.1.7
     */
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
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

        /**
         * Builder.
         * 
         * @since 0.1.7
         */
        private Builder() {
        }

        /**
         * kind.
         * 
         * @param kind kind
         * @return the result
         * @since 0.1.7
         */
        public Builder kind(String kind) {
            this.kind = StepKind.fromValue(kind);
            return this;
        }

        /**
         * kind.
         * 
         * @param kind kind
         * @return the result
         * @since 0.1.7
         */
        public Builder kind(StepKind kind) {
            this.kind = kind != null ? kind : StepKind.AGENT;
            return this;
        }

        /**
         * operatorId.
         * 
         * @param operatorId operatorId
         * @return the result
         * @since 0.1.7
         */
        public Builder operatorId(String operatorId) {
            this.operatorId = operatorId;
            return this;
        }

        /**
         * agentId.
         * 
         * @param agentId agentId
         * @return the result
         * @since 0.1.7
         */
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        /**
         * role.
         * 
         * @param role role
         * @return the result
         * @since 0.1.7
         */
        public Builder role(String role) {
            this.role = role;
            return this;
        }

        /**
         * nodeId.
         * 
         * @param nodeId nodeId
         * @return the result
         * @since 0.1.7
         */
        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        /**
         * inputs.
         * 
         * @param inputs inputs
         * @return the result
         * @since 0.1.7
         */
        public Builder inputs(Object inputs) {
            this.inputs = inputs;
            return this;
        }

        /**
         * outputs.
         * 
         * @param outputs outputs
         * @return the result
         * @since 0.1.7
         */
        public Builder outputs(Object outputs) {
            this.outputs = outputs;
            return this;
        }

        /**
         * error.
         * 
         * @param error error
         * @return the result
         * @since 0.1.7
         */
        public Builder error(Object error) {
            this.error = error;
            return this;
        }

        /**
         * startTimeMs.
         * 
         * @param startTimeMs startTimeMs
         * @return the result
         * @since 0.1.7
         */
        public Builder startTimeMs(Long startTimeMs) {
            this.startTimeMs = startTimeMs;
            return this;
        }

        /**
         * endTimeMs.
         * 
         * @param endTimeMs endTimeMs
         * @return the result
         * @since 0.1.7
         */
        public Builder endTimeMs(Long endTimeMs) {
            this.endTimeMs = endTimeMs;
            return this;
        }

        /**
         * detail.
         * 
         * @param detail detail
         * @return the result
         * @since 0.1.7
         */
        public Builder detail(Object detail) {
            this.detail = detail;
            return this;
        }

        /**
         * reward.
         * 
         * @param reward reward
         * @return the result
         * @since 0.1.7
         */
        public Builder reward(Double reward) {
            this.reward = reward;
            return this;
        }

        /**
         * promptTokenIds.
         * 
         * @param promptTokenIds promptTokenIds
         * @return the result
         * @since 0.1.7
         */
        public Builder promptTokenIds(List<Integer> promptTokenIds) {
            this.promptTokenIds = promptTokenIds;
            return this;
        }

        /**
         * completionTokenIds.
         * 
         * @param completionTokenIds completionTokenIds
         * @return the result
         * @since 0.1.7
         */
        public Builder completionTokenIds(List<Integer> completionTokenIds) {
            this.completionTokenIds = completionTokenIds;
            return this;
        }

        /**
         * logprobs.
         * 
         * @param logprobs logprobs
         * @return the result
         * @since 0.1.7
         */
        public Builder logprobs(Object logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        /**
         * meta.
         * 
         * @param meta meta
         * @return the result
         * @since 0.1.7
         */
        public Builder meta(Map<String, Object> meta) {
            this.meta = meta;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public TrajectoryStep build() {
            return new TrajectoryStep(kind, operatorId, agentId, role, nodeId, inputs, outputs, error, startTimeMs,
                    endTimeMs, detail, reward, promptTokenIds, completionTokenIds, logprobs, meta);
        }
    }
}
