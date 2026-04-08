/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.List;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.Trajectory.
 */
public class Trajectory {

    private String caseId;
    private String executionId;
    private String traceId;
    private List<TrajectoryStep> steps;
    private List<int[]> edges;

    public Trajectory() {
    }

    public Trajectory(String caseId, String executionId, String traceId, List<TrajectoryStep> steps, List<int[]> edges) {
        this.caseId = caseId;
        this.executionId = executionId;
        this.traceId = traceId;
        this.steps = steps;
        this.edges = edges;
    }

    public static Builder builder() { return new Builder(); }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public List<TrajectoryStep> getSteps() { return steps; }
    public void setSteps(List<TrajectoryStep> steps) { this.steps = steps; }
    public List<int[]> getEdges() { return edges; }
    public void setEdges(List<int[]> edges) { this.edges = edges; }

    public static final class Builder {
        private String caseId;
        private String executionId;
        private String traceId;
        private List<TrajectoryStep> steps;
        private List<int[]> edges;

        private Builder() {
        }

        public Builder caseId(String caseId) { this.caseId = caseId; return this; }
        public Builder executionId(String executionId) { this.executionId = executionId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder steps(List<TrajectoryStep> steps) { this.steps = steps; return this; }
        public Builder edges(List<int[]> edges) { this.edges = edges; return this; }

        public Trajectory build() {
            return new Trajectory(caseId, executionId, traceId, steps, edges);
        }
    }
}