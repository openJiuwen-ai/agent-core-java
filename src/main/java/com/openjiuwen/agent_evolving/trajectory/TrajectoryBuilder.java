/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.builder.TrajectoryBuilder.
 * Trajectory assembler for both online and offline paths.
 *
 * Responsibilities:
 * - Step accumulation (steps ordered by insertion)
 * - Cost accumulation (input_tokens/output_tokens)
 * - Final Trajectory assembly
 *
 * Usage:
 * <pre>
 * TrajectoryBuilder builder = TrajectoryBuilder.builder()
 *     .sessionId("conv_123")
 *     .source("online")
 *     .build();
 * builder.recordStep(step);
 * Trajectory trajectory = builder.buildTrajectory();
 * </pre>
 */
public class TrajectoryBuilder {

    private String sessionId;
    private String source;
    private String caseId;
    private String memberId;
    private Map<String, Object> meta;
    private List<TrajectoryStep> steps;
    private Map<String, Integer> cost;
    private Long startTimeMs;

    public TrajectoryBuilder() {
        this.steps = new ArrayList<>();
        this.cost = new HashMap<>();
        this.cost.put("input_tokens", 0);
        this.cost.put("output_tokens", 0);
        this.meta = new LinkedHashMap<>();
    }

    public TrajectoryBuilder(String sessionId, String source, String caseId, String memberId, Map<String, Object> meta) {
        this.sessionId = sessionId;
        this.source = source;
        this.caseId = caseId;
        this.memberId = memberId;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
        if (memberId != null) {
            this.meta.putIfAbsent("member_id", memberId);
        }
        this.steps = new ArrayList<>();
        this.cost = new HashMap<>();
        this.cost.put("input_tokens", 0);
        this.cost.put("output_tokens", 0);
        this.startTimeMs = null;
    }

    public static Builder builder() { return new Builder(); }

    /**
     * Record a step and accumulate cost.
     *
     * @param step Step to record
     */
    public void recordStep(TrajectoryStep step) {
        this.steps.add(step);

        // Accumulate token usage from LLM steps
        if (step.getKindEnum() == StepKind.LLM) {
            Object detail = step.getInputs();
            if (detail instanceof LLMCallDetail) {
                LLMCallDetail llmDetail = (LLMCallDetail) detail;
                Map<String, Object> usage = llmDetail.getUsage();
                if (usage != null) {
                    int promptTokens = usage.containsKey("prompt_tokens") 
                        ? ((Number) usage.get("prompt_tokens")).intValue() : 0;
                    int completionTokens = usage.containsKey("completion_tokens")
                        ? ((Number) usage.get("completion_tokens")).intValue() : 0;
                    this.cost.merge("input_tokens", promptTokens, Integer::sum);
                    this.cost.merge("output_tokens", completionTokens, Integer::sum);
                }
            }
        }

        // Record start time
        if (this.startTimeMs == null && step.getStartTimeMs() != null) {
            this.startTimeMs = step.getStartTimeMs();
        }
    }

    /**
     * Assemble Trajectory.
     *
     * @return Assembled Trajectory with all steps and metadata
     */
    public Trajectory buildTrajectory() {
        String executionId = UUID.randomUUID().toString();

        Map<String, Object> trajectoryMeta = new LinkedHashMap<>();
        if (this.memberId != null) {
            trajectoryMeta.put("member_id", this.memberId);
        }
        trajectoryMeta.putAll(this.meta);

        Map<String, Integer> finalCost = null;
        if (this.cost.get("input_tokens") > 0 || this.cost.get("output_tokens") > 0) {
            finalCost = new HashMap<>(this.cost);
        }

        return Trajectory.builder()
                .executionId(executionId)
                .sessionId(this.sessionId)
                .source(this.source)
                .caseId(this.caseId)
                .steps(new ArrayList<>(this.steps))
                .cost(finalCost)
                .meta(trajectoryMeta)
                .build();
    }

    // Getters
    public String getSessionId() { return sessionId; }
    public String getSource() { return source; }
    public String getCaseId() { return caseId; }
    public String getMemberId() { return memberId; }
    public Map<String, Object> getMeta() { return meta; }
    public List<TrajectoryStep> getSteps() { return steps; }
    public Map<String, Integer> getCost() { return cost; }
    public Long getStartTimeMs() { return startTimeMs; }

    // Setters
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setSource(String source) { this.source = source; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    public static final class Builder {
        private String sessionId;
        private String source;
        private String caseId;
        private String memberId;
        private Map<String, Object> meta;

        private Builder() {
            this.meta = new LinkedHashMap<>();
        }

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder caseId(String caseId) { this.caseId = caseId; return this; }
        public Builder memberId(String memberId) { this.memberId = memberId; return this; }
        public Builder meta(Map<String, Object> meta) {
            this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
            return this;
        }

        public TrajectoryBuilder build() {
            return new TrajectoryBuilder(sessionId, source, caseId, memberId, meta);
        }
    }
}