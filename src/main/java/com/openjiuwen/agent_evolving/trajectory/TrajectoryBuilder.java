/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trajectory assembler for both online and offline paths.
 * <p>
 * Mirrors Python's {@code TrajectoryBuilder} in
 * {@code openjiuwen/agent_evolving/trajectory/builder.py}.
 * </p>
 */
public class TrajectoryBuilder {

    private String sessionId;
    private String source;
    private String caseId;
    private String memberId;
    private Integer maxSteps;
    private Map<String, Object> meta;
    private List<TrajectoryStep> steps;
    private Map<String, Integer> cost;
    private Long startTimeMs;

    public TrajectoryBuilder() {
        this(null, null, null, null, null, null);
    }

    public TrajectoryBuilder(
            String sessionId,
            String source,
            String caseId,
            String memberId,
            Map<String, Object> meta,
            Integer maxSteps) {
        if (maxSteps != null && maxSteps < 1) {
            throw new IllegalArgumentException("max_steps must be >= 1");
        }
        this.sessionId = sessionId;
        this.source = source;
        this.caseId = caseId;
        this.memberId = memberId;
        this.maxSteps = maxSteps;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
        if (memberId != null) {
            this.meta.putIfAbsent("member_id", memberId);
        }
        this.steps = new ArrayList<>();
        this.cost = new LinkedHashMap<>();
        this.cost.put("input_tokens", 0);
        this.cost.put("output_tokens", 0);
        this.startTimeMs = null;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Record a step and accumulate cost.
     *
     * @param step step to record
     */
    public void recordStep(TrajectoryStep step) {
        this.steps.add(step);
        if (this.maxSteps != null && this.steps.size() > this.maxSteps) {
            this.steps = new ArrayList<>(this.steps.subList(this.steps.size() - this.maxSteps, this.steps.size()));
        }

        if (step.getKindEnum() == StepKind.LLM && step.getDetail() instanceof LLMCallDetail llmDetail) {
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

        if (this.startTimeMs == null && step.getStartTimeMs() != null) {
            this.startTimeMs = step.getStartTimeMs();
        }
    }

    /**
     * Assemble the final trajectory.
     *
     * @return assembled trajectory
     */
    public Trajectory build() {
        Map<String, Integer> finalCost = null;
        if (this.cost.get("input_tokens") > 0 || this.cost.get("output_tokens") > 0) {
            finalCost = new LinkedHashMap<>(this.cost);
        }

        return Trajectory.builder()
                .executionId(UUID.randomUUID().toString())
                .sessionId(this.sessionId)
                .source(this.source)
                .caseId(this.caseId)
                .steps(new ArrayList<>(this.steps))
                .cost(finalCost)
                .meta(new LinkedHashMap<>(this.meta))
                .build();
    }

    /**
     * Backward-compatible alias used by earlier translated callers.
     *
     * @return assembled trajectory
     */
    public Trajectory buildTrajectory() {
        return build();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSource() {
        return source;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getMemberId() {
        return memberId;
    }

    public Integer getMaxSteps() {
        return maxSteps;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public List<TrajectoryStep> getSteps() {
        return steps;
    }

    public Map<String, Integer> getCost() {
        return cost;
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public void setMaxSteps(Integer maxSteps) {
        if (maxSteps != null && maxSteps < 1) {
            throw new IllegalArgumentException("max_steps must be >= 1");
        }
        this.maxSteps = maxSteps;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
        if (this.memberId != null) {
            this.meta.putIfAbsent("member_id", this.memberId);
        }
    }

    public static final class Builder {
        private String sessionId;
        private String source;
        private String caseId;
        private String memberId;
        private Integer maxSteps;
        private Map<String, Object> meta;

        private Builder() {
            this.meta = new LinkedHashMap<>();
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        public Builder memberId(String memberId) {
            this.memberId = memberId;
            return this;
        }

        public Builder maxSteps(Integer maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        public Builder meta(Map<String, Object> meta) {
            this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
            return this;
        }

        public TrajectoryBuilder build() {
            return new TrajectoryBuilder(sessionId, source, caseId, memberId, meta, maxSteps);
        }
    }
}
