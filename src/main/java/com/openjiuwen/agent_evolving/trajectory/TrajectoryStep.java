/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.LinkedHashMap;
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
    private Map<String, Object> meta;

    public TrajectoryStep() {
        this.kind = StepKind.AGENT;
        this.meta = new LinkedHashMap<>();
    }

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
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

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
        this(StepKind.fromValue(kind), operatorId, agentId, role, nodeId, inputs, outputs, error, startTimeMs, endTimeMs, meta);
    }

    public static Builder builder() { return new Builder(); }
    public String getKind() { return kind != null ? kind.value() : StepKind.AGENT.value(); }
    public StepKind getKindEnum() { return kind != null ? kind : StepKind.AGENT; }
    public void setKind(String kind) { this.kind = StepKind.fromValue(kind); }
    public void setKind(StepKind kind) { this.kind = kind != null ? kind : StepKind.AGENT; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public Object getInputs() { return inputs; }
    public void setInputs(Object inputs) { this.inputs = inputs; }
    public Object getOutputs() { return outputs; }
    public void setOutputs(Object outputs) { this.outputs = outputs; }
    public Object getError() { return error; }
    public void setError(Object error) { this.error = error; }
    public Long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(Long startTimeMs) { this.startTimeMs = startTimeMs; }
    public Long getEndTimeMs() { return endTimeMs; }
    public void setEndTimeMs(Long endTimeMs) { this.endTimeMs = endTimeMs; }
    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>(); }

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
        private Map<String, Object> meta;

        private Builder() {
        }

        public Builder kind(String kind) { this.kind = StepKind.fromValue(kind); return this; }
        public Builder kind(StepKind kind) { this.kind = kind != null ? kind : StepKind.AGENT; return this; }
        public Builder operatorId(String operatorId) { this.operatorId = operatorId; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder role(String role) { this.role = role; return this; }
        public Builder nodeId(String nodeId) { this.nodeId = nodeId; return this; }
        public Builder inputs(Object inputs) { this.inputs = inputs; return this; }
        public Builder outputs(Object outputs) { this.outputs = outputs; return this; }
        public Builder error(Object error) { this.error = error; return this; }
        public Builder startTimeMs(Long startTimeMs) { this.startTimeMs = startTimeMs; return this; }
        public Builder endTimeMs(Long endTimeMs) { this.endTimeMs = endTimeMs; return this; }
        public Builder meta(Map<String, Object> meta) { this.meta = meta; return this; }

        public TrajectoryStep build() {
            return new TrajectoryStep(kind, operatorId, agentId, role, nodeId, inputs, outputs, error, startTimeMs, endTimeMs, meta);
        }
    }
}
