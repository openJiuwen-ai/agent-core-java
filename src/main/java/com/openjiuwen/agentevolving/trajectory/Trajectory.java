/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.Trajectory.
 * 
 * @since 0.1.7
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

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> meta = new LinkedHashMap<>();

    /**
     * Trajectory.
     * 
     * @since 0.1.7
     */
    public Trajectory() {
    }

    /**
     * Trajectory.
     * 
     * @param caseId caseId
     * @param executionId executionId
     * @param traceId traceId
     * @param steps steps
     * @param edges edges
     * @since 0.1.7
     */
    public Trajectory(String caseId, String executionId, String traceId, List<TrajectoryStep> steps,
            List<int[]> edges) {
        this(caseId, executionId, traceId, steps, edges, "offline", null, null, null);
    }

    /**
     * Trajectory.
     * 
     * @param caseId caseId
     * @param executionId executionId
     * @param traceId traceId
     * @param steps steps
     * @param edges edges
     * @param source source
     * @param sessionId sessionId
     * @param cost cost
     * @param meta meta
     * @since 0.1.7
     */
    public Trajectory(String caseId, String executionId, String traceId, List<TrajectoryStep> steps, List<int[]> edges,
            String source, String sessionId, Map<String, Integer> cost, Map<String, Object> meta) {
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
     * getTraceId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * setTraceId.
     * 
     * @param traceId traceId
     * @since 0.1.7
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * getSteps.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TrajectoryStep> getSteps() {
        return steps;
    }

    /**
     * setSteps.
     * 
     * @param steps steps
     * @since 0.1.7
     */
    public void setSteps(List<TrajectoryStep> steps) {
        this.steps = steps;
    }

    /**
     * getEdges.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<int[]> getEdges() {
        return edges;
    }

    /**
     * setEdges.
     * 
     * @param edges edges
     * @since 0.1.7
     */
    public void setEdges(List<int[]> edges) {
        this.edges = edges;
    }

    /**
     * getSource.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSource() {
        return source;
    }

    /**
     * setSource.
     * 
     * @param source source
     * @since 0.1.7
     */
    public void setSource(String source) {
        this.source = source != null ? source : "offline";
    }

    /**
     * getSessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * setSessionId.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * getCost.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Integer> getCost() {
        return cost;
    }

    /**
     * setCost.
     * 
     * @param cost cost
     * @since 0.1.7
     */
    public void setCost(Map<String, Integer> cost) {
        this.cost = cost != null ? new LinkedHashMap<>(cost) : null;
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
        private String caseId;
        private String executionId;
        private String traceId;
        private List<TrajectoryStep> steps;
        private List<int[]> edges;
        private String source = "offline";
        private String sessionId;
        private Map<String, Integer> cost;
        private Map<String, Object> meta;

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
         * traceId.
         * 
         * @param traceId traceId
         * @return the result
         * @since 0.1.7
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * steps.
         * 
         * @param steps steps
         * @return the result
         * @since 0.1.7
         */
        public Builder steps(List<TrajectoryStep> steps) {
            this.steps = steps;
            return this;
        }

        /**
         * edges.
         * 
         * @param edges edges
         * @return the result
         * @since 0.1.7
         */
        public Builder edges(List<int[]> edges) {
            this.edges = edges;
            return this;
        }

        /**
         * source.
         * 
         * @param source source
         * @return the result
         * @since 0.1.7
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * sessionId.
         * 
         * @param sessionId sessionId
         * @return the result
         * @since 0.1.7
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * cost.
         * 
         * @param cost cost
         * @return the result
         * @since 0.1.7
         */
        public Builder cost(Map<String, Integer> cost) {
            this.cost = cost;
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
        public Trajectory build() {
            return new Trajectory(caseId, executionId, traceId, steps, edges, source, sessionId, cost, meta);
        }
    }
}
