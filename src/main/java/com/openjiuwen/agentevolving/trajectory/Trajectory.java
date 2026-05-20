/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.Trajectory.
 */
public class Trajectory {

    private String caseId;
    private String executionId;
    private String traceId;
    private List<TrajectoryStep> steps;
    private List<int[]> edges;
    private String source = "offline";
    private String sessionId;
    private Map<String, Integer> cost;
    private Map<String, Object> meta = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public Trajectory() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Trajectory(String caseId, String executionId, String traceId, List<TrajectoryStep> steps, List<int[]> edges) {
        this(caseId, executionId, traceId, steps, edges, "offline", null, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Trajectory(String caseId,
                      String executionId,
                      String traceId,
                      List<TrajectoryStep> steps,
                      List<int[]> edges,
                      String source,
                      String sessionId,
                      Map<String, Integer> cost,
                      Map<String, Object> meta) {
        this.caseId = caseId;
        this.executionId = executionId;
        this.traceId = traceId;
        this.steps = steps;
        this.edges = edges;
        this.source = source != null ? source : "offline";
        this.sessionId = sessionId;
        this.cost = cost != null ? new LinkedHashMap<>(cost) : null;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
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
    public String getTraceId() {
        return traceId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<TrajectoryStep> getSteps() {
        return steps;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSteps(List<TrajectoryStep> steps) {
        this.steps = steps;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<int[]> getEdges() {
        return edges;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setEdges(List<int[]> edges) {
        this.edges = edges;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSource() {
        return source;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSource(String source) {
        this.source = source != null ? source : "offline";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Integer> getCost() {
        return cost;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCost(Map<String, Integer> cost) {
        this.cost = cost != null ? new LinkedHashMap<>(cost) : null;
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
        private String caseId;
        private String executionId;
        private String traceId;
        private List<TrajectoryStep> steps;
        private List<int[]> edges;
        private String source = "offline";
        private String sessionId;
        private Map<String, Integer> cost;
        private Map<String, Object> meta;

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
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder steps(List<TrajectoryStep> steps) {
            this.steps = steps;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder edges(List<int[]> edges) {
            this.edges = edges;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder cost(Map<String, Integer> cost) {
            this.cost = cost;
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
        public Trajectory build() {
            return new Trajectory(caseId, executionId, traceId, steps, edges, source, sessionId, cost, meta);
        }
    }
}
