/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code Trajectory} in {@code openjiuwen.agent_evolving.trajectory.types}.
 * Complete execution trajectory.
 */
public class Trajectory {

    private String executionId;
    /** Unique identifier for this execution. */

    private List<TrajectoryStep> steps;
    /** Ordered list of execution steps. */

    private String source = "offline";
    /** Execution source: 'online' (deepagents) | 'offline' (trainer) */

    private String caseId;
    /** Offline: dataset case identifier. Online: None. */

    private String sessionId;
    /** Online: conversation session ID. Offline: can reuse caseId or None. */

    private String traceId;

    private Map<String, Integer> cost;
    /** Aggregated cost metrics: input_tokens, output_tokens. */

    private List<int[]> edges;

    private Map<String, Object> meta;
    /** Extension metadata for trajectory-level attributes. */

    public Trajectory() {
        this.steps = new ArrayList<>();
        this.meta = new LinkedHashMap<>();
        this.source = "offline";
    }

    public Trajectory(String executionId, String sessionId, String source, 
                      List<TrajectoryStep> steps, Map<String, Integer> cost, Map<String, Object> meta) {
        this.executionId = executionId;
        this.sessionId = sessionId;
        this.source = source != null ? source : "offline";
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
        this.cost = cost;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    public Trajectory(String executionId, List<TrajectoryStep> steps, String source,
                      String caseId, String sessionId, String traceId,
                      Map<String, Integer> cost, List<int[]> edges, Map<String, Object> meta) {
        this.executionId = executionId;
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
        this.source = source != null ? source : "offline";
        this.caseId = caseId;
        this.sessionId = sessionId;
        this.traceId = traceId;
        this.cost = cost;
        this.edges = edges;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    public static Builder builder() { return new Builder(); }

    // Getters
    public String getExecutionId() { return executionId; }
    public List<TrajectoryStep> getSteps() { return steps; }
    public String getSource() { return source; }
    public String getCaseId() { return caseId; }
    public String getSessionId() { return sessionId; }
    public String getTraceId() { return traceId; }
    public Map<String, Integer> getCost() { return cost; }
    public List<int[]> getEdges() { return edges; }
    public Map<String, Object> getMeta() { return meta; }

    // Setters
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public void setSteps(List<TrajectoryStep> steps) {
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }
    public void setSource(String source) { this.source = source != null ? source : "offline"; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public void setCost(Map<String, Integer> cost) { this.cost = cost; }
    public void setEdges(List<int[]> edges) { this.edges = edges; }
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    public static final class Builder {
        private String executionId;
        private List<TrajectoryStep> steps;
        private String source;
        private String caseId;
        private String sessionId;
        private String traceId;
        private Map<String, Integer> cost;
        private List<int[]> edges;
        private Map<String, Object> meta;

        private Builder() {
            this.steps = new ArrayList<>();
            this.meta = new LinkedHashMap<>();
            this.source = "offline";
        }

        public Builder executionId(String executionId) { this.executionId = executionId; return this; }
        public Builder steps(List<TrajectoryStep> steps) {
            this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
            return this;
        }
        public Builder source(String source) { this.source = source; return this; }
        public Builder caseId(String caseId) { this.caseId = caseId; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder cost(Map<String, Integer> cost) { this.cost = cost; return this; }
        public Builder edges(List<int[]> edges) { this.edges = edges; return this; }
        public Builder meta(Map<String, Object> meta) {
            this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
            return this;
        }

        public Trajectory build() {
            return new Trajectory(executionId, steps, source, caseId, sessionId, traceId, cost, edges, meta);
        }
    }
}
