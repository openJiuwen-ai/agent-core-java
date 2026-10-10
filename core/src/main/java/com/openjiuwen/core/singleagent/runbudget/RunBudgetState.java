/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-invocation runtime state of the run budget (turn and time dimensions).
 *
 * <p>Lives in {@link AgentCallbackContext#getExtra()} under {@link #STATE_KEY} and is destroyed
 * with the invocation; never stored on the shared agent instance. Fields touched by tool-lane
 * threads ({@link #markNewToolResult()}, host stall report) use atomic holders; all other fields
 * are confined to the main-loop thread.</p>
 *
 * @since 2026-10-08
 */
public class RunBudgetState {
    /** Key under which the state is stored in {@link AgentCallbackContext#getExtra()}. */
    public static final String STATE_KEY = "run_budget_state";

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private boolean isTurnEnabled;
    private GuaranteeDecision guarantee;
    private int firstCheckpoint;
    private int roundCount;
    private int nextCheckpoint;
    private int currentInterval;
    private int consecutiveStalls;
    private boolean isEscalated;
    private int lastGentleReminderRound;
    private boolean isSafetyValveTriggered;
    private boolean isFinalRoundEmitted;
    private final AtomicBoolean terminated = new AtomicBoolean();
    private String lastModelContent = "";
    private final AtomicBoolean newToolResultSinceCheckpoint = new AtomicBoolean();
    private final AtomicReference<HostStallReport> hostStallReport = new AtomicReference<>();
    private boolean isTimeEnabled;
    private long startNanos;
    private long deadlineNanos;
    private double totalBudgetSeconds;
    private double nearDeadlineThresholdSeconds;
    private boolean isNearDeadlinePromptSent;
    private String conversationId = "";
    private String sessionId = "";
    private String agentName = "";
    private String agentId = "";

    private RunBudgetState() {
    }

    /**
     * Returns the state stored in the given extra map, when present.
     *
     * @param extra callback-context extra map, may be null
     * @return the state, or empty when absent or of unexpected type
     */
    public static Optional<RunBudgetState> from(Map<String, Object> extra) {
        if (extra == null) {
            return Optional.empty();
        }
        Object value = extra.get(STATE_KEY);
        return value instanceof RunBudgetState state ? Optional.of(state) : Optional.empty();
    }

    /**
     * Initializes the per-invocation state from the immutable config and the invoke context.
     *
     * @param config run-budget config
     * @param ctx invoke-scoped callback context
     * @return the initialized state
     */
    public static RunBudgetState initialize(RunBudgetConfig config, AgentCallbackContext ctx) {
        RunBudgetState state = new RunBudgetState();
        state.captureContextIdentity(ctx);
        state.isTurnEnabled = config.isTurnEnabled();
        if (state.isTurnEnabled) {
            state.initializeTurnDimension(config);
        }
        state.isTimeEnabled = config.isTimeEnabled();
        if (state.isTimeEnabled) {
            state.initializeTimeDimension(config);
        }
        return state;
    }

    private void captureContextIdentity(AgentCallbackContext ctx) {
        if (ctx.getInputs() instanceof InvokeInputs invokeInputs && invokeInputs.getConversationId() != null) {
            this.conversationId = invokeInputs.getConversationId();
        }
        if (ctx.getSession() != null && ctx.getSession().getSessionId() != null) {
            this.sessionId = ctx.getSession().getSessionId();
        }
        if (ctx.getAgent() != null && ctx.getAgent().getCard() != null) {
            this.agentName = String.valueOf(ctx.getAgent().getCard().getName());
            this.agentId = String.valueOf(ctx.getAgent().getCard().getId());
        }
    }

    private void initializeTurnDimension(RunBudgetConfig config) {
        boolean isConfigured = config.getSuggestedRounds() != null;
        int guaranteedRounds = isConfigured ? config.getSuggestedRounds() : config.getDefaultGuaranteedRounds();
        String source = isConfigured ? GuaranteeDecision.SOURCE_CONFIGURED : GuaranteeDecision.SOURCE_DEFAULT;
        int limit = config.getHardLimit() != null
                ? config.getHardLimit() : Math.max(guaranteedRounds * 2, 200);
        this.guarantee = new GuaranteeDecision(guaranteedRounds, source, limit);
        this.firstCheckpoint = config.getFirstCheckpoint() != null
                ? config.getFirstCheckpoint() : Math.max(10, guaranteedRounds);
        this.nextCheckpoint = this.firstCheckpoint;
    }

    private void initializeTimeDimension(RunBudgetConfig config) {
        this.totalBudgetSeconds = config.getTotalBudgetSeconds();
        this.nearDeadlineThresholdSeconds = config.getNearDeadlineThresholdSeconds();
        this.startNanos = System.nanoTime();
        this.deadlineNanos = this.startNanos + Math.multiplyExact((long) this.totalBudgetSeconds, NANOS_PER_SECOND);
    }

    /**
     * Returns whether the turn dimension is active for this invocation.
     *
     * @return true when the turn dimension is active
     */
    public boolean isTurnEnabled() {
        return isTurnEnabled;
    }

    /**
     * Returns whether the time dimension is active for this invocation.
     *
     * @return true when the time dimension is active
     */
    public boolean isTimeEnabled() {
        return isTimeEnabled;
    }

    /**
     * Returns the remaining wall-clock budget in seconds; negative when expired.
     *
     * @return remaining seconds
     */
    public double remainingSeconds() {
        return (deadlineNanos - System.nanoTime()) / (double) NANOS_PER_SECOND;
    }

    /**
     * Returns the remaining wall-clock budget in milliseconds; negative when expired.
     *
     * @return remaining milliseconds
     */
    public long remainingMillis() {
        return (deadlineNanos - System.nanoTime()) / 1_000_000L;
    }

    /**
     * Returns the elapsed wall-clock time in seconds since budget start.
     *
     * @return elapsed seconds
     */
    public double elapsedSeconds() {
        return (System.nanoTime() - startNanos) / NANOS_PER_SECOND;
    }

    /**
     * Returns the turn-dimension guarantee decision.
     *
     * @return guarantee decision, or null when the turn dimension is disabled
     */
    public GuaranteeDecision getGuarantee() {
        return guarantee;
    }

    /**
     * Returns the effective first checkpoint round.
     *
     * @return first checkpoint round
     */
    public int getFirstCheckpoint() {
        return firstCheckpoint;
    }

    /**
     * Returns the completed-round count (only exception-free attempts with a response).
     *
     * @return completed rounds
     */
    public int getRoundCount() {
        return roundCount;
    }

    /**
     * Increments the completed-round count by one.
     */
    public void incrementRoundCount() {
        this.roundCount++;
    }

    /**
     * Returns the next checkpoint round (the allowed-rounds watermark).
     *
     * @return next checkpoint round
     */
    public int getNextCheckpoint() {
        return nextCheckpoint;
    }

    /**
     * Returns the consecutive stalled-checkpoint count.
     *
     * @return consecutive stalls
     */
    public int getConsecutiveStalls() {
        return consecutiveStalls;
    }

    /**
     * Returns whether prompt strength has been isEscalated.
     *
     * @return true when isEscalated
     */
    public boolean isEscalated() {
        return isEscalated;
    }

    /**
     * Returns the round of the last gentle reminder.
     *
     * @return last gentle reminder round
     */
    public int getLastGentleReminderRound() {
        return lastGentleReminderRound;
    }

    /**
     * Records a gentle reminder emitted at the given round.
     *
     * @param round current round
     */
    public void markGentleReminder(int round) {
        this.lastGentleReminderRound = round;
    }

    /**
     * Returns whether the safety valve has been triggered.
     *
     * @return true when triggered
     */
    public boolean isSafetyValveTriggered() {
        return isSafetyValveTriggered;
    }

    /**
     * Marks the safety valve as triggered.
     */
    public void markSafetyValveTriggered() {
        this.isSafetyValveTriggered = true;
    }

    /**
     * Returns whether the final-round-granted event has been emitted.
     *
     * @return true when already emitted
     */
    public boolean isFinalRoundEmitted() {
        return isFinalRoundEmitted;
    }

    /**
     * Marks the final-round-granted event as emitted (idempotent against rail retries).
     */
    public void markFinalRoundEmitted() {
        this.isFinalRoundEmitted = true;
    }

    /**
     * Returns whether the budget has terminated the task.
     *
     * @return true when terminated
     */
    public boolean isTerminated() {
        return terminated.get();
    }

    /**
     * Atomically marks termination.
     *
     * @return true when this call performed the transition (false when already terminated)
     */
    public boolean markTerminated() {
        return terminated.compareAndSet(false, true);
    }

    /**
     * Returns the previous round's model content used as the change baseline.
     *
     * @return last model content, never null
     */
    public String getLastModelContent() {
        return lastModelContent;
    }

    /**
     * Returns whether a new tool result was observed since the last checkpoint.
     *
     * @return true when a tool completed since the last checkpoint
     */
    public boolean hasNewToolResultSinceCheckpoint() {
        return newToolResultSinceCheckpoint.get();
    }

    /**
     * Records a completed tool call; safe to call from tool-lane threads.
     */
    public void markNewToolResult() {
        newToolResultSinceCheckpoint.set(true);
    }

    /**
     * Records a progressed checkpoint: resets stalls and advances the interval schedule.
     *
     * @param intervalStep per-progress interval step
     * @param maxInterval interval ceiling
     * @param currentRound current round, used to compute the next checkpoint
     */
    public void recordProgress(int intervalStep, int maxInterval, int currentRound) {
        this.consecutiveStalls = 0;
        this.isEscalated = false;
        this.currentInterval = Math.min(this.currentInterval + intervalStep, maxInterval);
        int hardLimitCeiling = this.guarantee.hardLimit() + 1;
        this.nextCheckpoint = Math.min(currentRound + this.currentInterval, hardLimitCeiling);
    }

    /**
     * Records a stalled checkpoint and advances the schedule by the base step cadence.
     *
     * <p>Stalls never lengthen the interval (lengthening is the progress reward); the next
     * checkpoint advances by {@code max(currentInterval, intervalStep)} so stalled tasks keep
     * being evaluated and the escalation threshold stays reachable.</p>
     *
     * @param escalationThreshold consecutive-stall count that escalates prompt strength
     * @param intervalStep base interval step in rounds
     * @param currentRound current round, used to compute the next checkpoint
     */
    public void recordStall(int escalationThreshold, int intervalStep, int currentRound) {
        this.consecutiveStalls++;
        if (this.consecutiveStalls >= escalationThreshold) {
            this.isEscalated = true;
        }
        int advance = Math.max(this.currentInterval, intervalStep);
        int hardLimitCeiling = this.guarantee.hardLimit() + 1;
        this.nextCheckpoint = Math.min(currentRound + advance, hardLimitCeiling);
    }

    /**
     * Starts a new checkpoint window with the given content baseline.
     *
     * @param currentContent current model content, may be null
     */
    public void startNewWindow(String currentContent) {
        this.newToolResultSinceCheckpoint.set(false);
        this.lastModelContent = currentContent == null ? "" : currentContent;
    }

    /**
     * Records a host-reported no-progress fact; safe to call from tool-lane threads.
     *
     * @param evidence host evidence text
     * @param promptText host-provided prompt wording, may be null
     */
    public void reportHostStall(String evidence, String promptText) {
        hostStallReport.set(new HostStallReport(evidence, promptText));
    }

    /**
     * Returns whether a host stall report is pending in the current checkpoint window.
     *
     * @return true when the host reported no progress
     */
    public boolean hasHostStallReport() {
        return hostStallReport.get() != null;
    }

    /**
     * Consumes the pending host stall report, clearing the current window's report.
     *
     * @return the report, or empty when none is pending
     */
    public Optional<HostStallReport> consumeHostStallReport() {
        return Optional.ofNullable(hostStallReport.getAndSet(null));
    }

    /**
     * Returns the near-deadline threshold in seconds.
     *
     * @return threshold seconds
     */
    public double getNearDeadlineThresholdSeconds() {
        return nearDeadlineThresholdSeconds;
    }

    /**
     * Returns the total budget in seconds.
     *
     * @return total budget seconds
     */
    public double getTotalBudgetSeconds() {
        return totalBudgetSeconds;
    }

    /**
     * Returns whether the near-deadline prompt has already been sent.
     *
     * @return true when already sent
     */
    public boolean isNearDeadlinePromptSent() {
        return isNearDeadlinePromptSent;
    }

    /**
     * Marks the near-deadline prompt as sent (exactly once per invocation).
     */
    public void markNearDeadlinePromptSent() {
        this.isNearDeadlinePromptSent = true;
    }

    /**
     * Returns the conversation id captured at invoke start.
     *
     * @return conversation id, never null
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Returns the session id captured at invoke start.
     *
     * @return session id, never null
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Returns the agent name captured at invoke start.
     *
     * @return agent name, never null
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * Returns the agent id captured at invoke start.
     *
     * @return agent id, never null
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Host-reported no-progress fact with optional prompt wording.
     *
     * @param evidence host evidence text
     * @param promptText host-provided prompt wording, may be null
     * @since 2026-10-08
     */
    public record HostStallReport(String evidence, String promptText) {
    }
}
