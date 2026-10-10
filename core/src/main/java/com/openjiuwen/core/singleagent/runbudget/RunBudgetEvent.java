/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import com.openjiuwen.core.common.logging.events.BaseLogEvent;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.ModuleType;

import java.util.Map;

/**
 * Structured event payload for FEAT-057 run-budget and progress events.
 *
 * <p>Field set is fixed by the L2 contract (section 2.1); producers fill only the fields
 * relevant to the concrete event type, unset fields are omitted from {@link #toMap()}.</p>
 *
 * @since 2026-10-08
 */
public class RunBudgetEvent extends BaseLogEvent {
    private Integer guaranteedRounds;
    private String source;
    private Integer hardLimit;
    private Integer round;
    private Integer checkpoint;
    private Boolean progressed;
    private Integer consecutiveStalls;
    private Integer allowedRounds;
    private Integer nextInterval;
    private Boolean hostStallReported;
    private Boolean escalated;
    private Integer reminderInterval;
    private Integer finalRound;
    private Boolean instructionInjected;
    private String reason;
    private Double totalBudgetSeconds;
    private Double elapsedSeconds;
    private Double remainingSeconds;
    private Double thresholdSeconds;
    private Double nearDeadlineThresholdSeconds;
    private String terminatedAt;
    private String agentName;
    private String agentId;

    /**
     * Creates an event of the given type in the agent module.
     *
     * @param eventType log event type
     */
    public RunBudgetEvent(LogEventType eventType) {
        super();
        setEventType(eventType);
        setModuleType(ModuleType.AGENT);
    }

    /**
     * Returns the guaranteed rounds.
     *
     * @return guaranteed rounds
     */
    public Integer getGuaranteedRounds() {
        return guaranteedRounds;
    }

    /**
     * Returns the guarantee source.
     *
     * @return configured or default
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the absolute hard limit.
     *
     * @return hard limit
     */
    public Integer getHardLimit() {
        return hardLimit;
    }

    /**
     * Returns the round at which the event fired.
     *
     * @return round
     */
    public Integer getRound() {
        return round;
    }

    /**
     * Returns the checkpoint at which the evaluation ran.
     *
     * @return checkpoint round
     */
    public Integer getCheckpoint() {
        return checkpoint;
    }

    /**
     * Returns whether the checkpoint evaluation judged progress.
     *
     * @return progressed flag
     */
    public Boolean getProgressed() {
        return progressed;
    }

    /**
     * Returns the consecutive stall count.
     *
     * @return consecutive stalls
     */
    public Integer getConsecutiveStalls() {
        return consecutiveStalls;
    }

    /**
     * Returns the allowed-rounds watermark (next checkpoint round).
     *
     * @return allowed rounds
     */
    public Integer getAllowedRounds() {
        return allowedRounds;
    }

    /**
     * Returns the interval to the next checkpoint.
     *
     * @return next interval in rounds
     */
    public Integer getNextInterval() {
        return nextInterval;
    }

    /**
     * Returns whether a host stall report was consumed by this evaluation.
     *
     * @return host-stall-reported flag
     */
    public Boolean getHostStallReported() {
        return hostStallReported;
    }

    /**
     * Returns whether prompt strength was escalated.
     *
     * @return escalated flag
     */
    public Boolean getEscalated() {
        return escalated;
    }

    /**
     * Returns the gentle reminder interval.
     *
     * @return reminder interval in rounds
     */
    public Integer getReminderInterval() {
        return reminderInterval;
    }

    /**
     * Returns the granted final round.
     *
     * @return final round
     */
    public Integer getFinalRound() {
        return finalRound;
    }

    /**
     * Returns whether the safety-valve instruction was injected.
     *
     * @return instruction-injected flag
     */
    public Boolean getInstructionInjected() {
        return instructionInjected;
    }

    /**
     * Returns the termination reason.
     *
     * @return reason
     */
    public String getReason() {
        return reason;
    }

    /**
     * Returns the total time budget in seconds.
     *
     * @return total budget seconds
     */
    public Double getTotalBudgetSeconds() {
        return totalBudgetSeconds;
    }

    /**
     * Returns the elapsed seconds at termination.
     *
     * @return elapsed seconds
     */
    public Double getElapsedSeconds() {
        return elapsedSeconds;
    }

    /**
     * Returns the remaining seconds when the near-deadline prompt fired.
     *
     * @return remaining seconds
     */
    public Double getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * Returns the near-deadline threshold in seconds.
     *
     * @return threshold seconds
     */
    public Double getThresholdSeconds() {
        return thresholdSeconds;
    }

    /**
     * Returns the near-deadline threshold carried by the effective event.
     *
     * @return near-deadline threshold seconds
     */
    public Double getNearDeadlineThresholdSeconds() {
        return nearDeadlineThresholdSeconds;
    }

    /**
     * Returns the checkpoint kind at which termination fired.
     *
     * @return terminated-at marker
     */
    public String getTerminatedAt() {
        return terminatedAt;
    }

    /**
     * Returns the agent name.
     *
     * @return agent name
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * Returns the agent id.
     *
     * @return agent id
     */
    public String getAgentId() {
        return agentId;
    }

    void setGuaranteedRounds(Integer value) {
        this.guaranteedRounds = value;
    }

    void setSource(String value) {
        this.source = value;
    }

    void setHardLimit(Integer value) {
        this.hardLimit = value;
    }

    void setRound(Integer value) {
        this.round = value;
    }

    void setCheckpoint(Integer value) {
        this.checkpoint = value;
    }

    void setProgressed(Boolean value) {
        this.progressed = value;
    }

    void setConsecutiveStalls(Integer value) {
        this.consecutiveStalls = value;
    }

    void setAllowedRounds(Integer value) {
        this.allowedRounds = value;
    }

    void setNextInterval(Integer value) {
        this.nextInterval = value;
    }

    void setHostStallReported(Boolean value) {
        this.hostStallReported = value;
    }

    void setEscalated(Boolean value) {
        this.escalated = value;
    }

    void setReminderInterval(Integer value) {
        this.reminderInterval = value;
    }

    void setFinalRound(Integer value) {
        this.finalRound = value;
    }

    void setInstructionInjected(Boolean value) {
        this.instructionInjected = value;
    }

    void setReason(String value) {
        this.reason = value;
    }

    void setTotalBudgetSeconds(Double value) {
        this.totalBudgetSeconds = value;
    }

    void setElapsedSeconds(Double value) {
        this.elapsedSeconds = value;
    }

    void setRemainingSeconds(Double value) {
        this.remainingSeconds = value;
    }

    void setThresholdSeconds(Double value) {
        this.thresholdSeconds = value;
    }

    void setNearDeadlineThresholdSeconds(Double value) {
        this.nearDeadlineThresholdSeconds = value;
    }

    void setTerminatedAt(String value) {
        this.terminatedAt = value;
    }

    void setAgentName(String value) {
        this.agentName = value;
    }

    void setAgentId(String value) {
        this.agentId = value;
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "guaranteed_rounds", guaranteedRounds);
        putIfNotNull(map, "source", source);
        putIfNotNull(map, "hard_limit", hardLimit);
        putIfNotNull(map, "round", round);
        putIfNotNull(map, "checkpoint", checkpoint);
        putIfNotNull(map, "progressed", progressed);
        putIfNotNull(map, "consecutive_stalls", consecutiveStalls);
        putIfNotNull(map, "allowed_rounds", allowedRounds);
        putIfNotNull(map, "next_interval", nextInterval);
        putIfNotNull(map, "host_stall_reported", hostStallReported);
        putIfNotNull(map, "escalated", escalated);
        putIfNotNull(map, "reminder_interval", reminderInterval);
        putIfNotNull(map, "final_round", finalRound);
        putIfNotNull(map, "instruction_injected", instructionInjected);
        putIfNotNull(map, "reason", reason);
        putIfNotNull(map, "total_budget_seconds", totalBudgetSeconds);
        putIfNotNull(map, "elapsed_seconds", elapsedSeconds);
        putIfNotNull(map, "remaining_seconds", remainingSeconds);
        putIfNotNull(map, "threshold_seconds", thresholdSeconds);
        putIfNotNull(map, "near_deadline_threshold_seconds", nearDeadlineThresholdSeconds);
        putIfNotNull(map, "terminated_at", terminatedAt);
        putIfNotNull(map, "agent_name", agentName);
        putIfNotNull(map, "agent_id", agentId);
    }
}
