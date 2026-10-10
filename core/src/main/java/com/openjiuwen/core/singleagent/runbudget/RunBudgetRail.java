/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.LogLevel;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.RetryRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Run-budget rail implementing FEAT-057 (main loop run budget and progress tracking).
 *
 * <p>Carries both budget dimensions over the existing rail primitives: prompts go through the
 * steering queue, termination goes through {@code requestForceFinish}, and progress facts are
 * observed in {@code afterModelCall}/{@code afterToolCall}. Priority 10 keeps the rail behind
 * business rails (50-100); every hook first honors a pending force-finish request so overlay
 * rail termination decisions are never overridden. Rails with a lower priority must not write
 * {@code requestForceFinish}/{@code requestRetry} (they would overwrite this rail's decisions).</p>
 *
 * <p>Termination only stages the force-finish request and emits the event; persisting contexts
 * is the framework's duty — both force-finish branches (the model leg in the main loop and the
 * tool leg via {@code restoreForceFinish}/{@code propagateForceFinish}) save contexts before
 * finishing. The rail deliberately never saves on its own so a failing best-effort save can
 * never swallow the termination itself.</p>
 *
 * <p>The rail self-binds a steering queue in {@code beforeInvoke} when the host did not bind
 * one; because priority 10 runs last in the fan-out, a higher-priority rail that pushes
 * steering in its own {@code beforeInvoke} needs the host to pre-bind the queue (e.g. via the
 * {@code _steering_queue} input) for that message to land.</p>
 *
 * @since 2026-10-09
 */
public class RunBudgetRail extends AgentRail {
    /** Registration priority: behind all business rails, ahead of observation-only rails. */
    public static final int RAIL_PRIORITY = 10;

    /** Terminated-at marker for expiry detected around the model call. */
    public static final String TERMINATED_AT_MODEL_CALL = "model_call";

    /** Terminated-at marker for expiry detected before tool dispatch. */
    public static final String TERMINATED_AT_TOOL_DISPATCH = "tool_dispatch";

    private static final String REASON_TURN_EXHAUSTED = "turn_budget_exhausted";
    private static final String REASON_TIME_EXHAUSTED = "time_budget_exhausted";
    private static final String TURN_PREFIX = "[轮次预算] ";
    private static final String TIME_PREFIX = "[时间预算] ";
    private static final String STALL_PROMPT = TURN_PREFIX
            + "检测到任务连续多轮没有实质进展，请考虑更换策略，或基于已有信息收尾并给出结论。";
    private static final String STALL_PROMPT_ESCALATED = TURN_PREFIX
            + "任务已连续多个检查点没有实质进展，请立即基于已有信息收尾，给出最终结论，不要再重复相同的尝试。";
    private static final String GENTLE_REMINDER = TURN_PREFIX
            + "任务已运行较多轮次，请评估是否可以基于当前已获得的信息收尾并给出答复。";
    private static final String SAFETY_VALVE_INSTRUCTION = TURN_PREFIX
            + "已达到轮次预算绝对上限，必须立即收尾：请基于已有信息直接给出最终答复，不要再调用任何工具。";
    private static final String NEAR_DEADLINE_PROMPT = TIME_PREFIX
            + "本次执行的时间预算临近截止，请基于已有信息尽快收尾并给出结论。";
    private static final String TURN_TERMINATED_OUTPUT = "已达到轮次预算绝对上限，任务被终止。";
    private static final String TIME_TERMINATED_OUTPUT = "本次执行的时间预算已到期，任务被终止。";

    private final RunBudgetConfig config;

    /**
     * Creates the rail with the given immutable config.
     *
     * @param config run-budget config, must not be null
     */
    public RunBudgetRail(RunBudgetConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("RunBudgetConfig must not be null");
        }
        this.config = config;
        setPriority(RAIL_PRIORITY);
    }

    /**
     * Returns the immutable config carried by this rail.
     *
     * @return run-budget config
     */
    public RunBudgetConfig getConfig() {
        return config;
    }

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        if (ctx.hasForceFinishRequest()) {
            return;
        }
        if (ctx.getSteeringQueue() == null) {
            ctx.bindSteeringQueue(new ConcurrentLinkedQueue<>());
        }
        RunBudgetState state = RunBudgetState.initialize(config, ctx);
        ctx.getExtra().put(RunBudgetState.STATE_KEY, state);
        emitGuaranteeEffective(state);
        emitTimeBudgetEffective(state);
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        Optional<RunBudgetState> stateOptional = RunBudgetState.from(ctx.getExtra());
        if (stateOptional.isEmpty() || ctx.hasForceFinishRequest()) {
            return;
        }
        RunBudgetState state = stateOptional.get();
        if (state.isTimeEnabled()) {
            double remaining = state.remainingSeconds();
            if (remaining <= 0) {
                terminateForTimeBudget(ctx, state, RunBudgetRail.TERMINATED_AT_MODEL_CALL);
                return;
            }
            maybePromptNearDeadline(ctx, state, remaining);
        }
        if (state.isTurnEnabled() && state.isSafetyValveTriggered() && !state.isFinalRoundEmitted()) {
            state.markFinalRoundEmitted();
            RunBudgetEvent event = newEvent(state, LogEventType.TURN_BUDGET_FINAL_ROUND_GRANTED);
            event.setRound(state.getRoundCount() + 1);
            event.setHardLimit(state.getGuarantee().hardLimit());
            RunBudgetEvents.emit("run budget final round granted", LogEventType.TURN_BUDGET_FINAL_ROUND_GRANTED,
                    event);
        }
    }

    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        Optional<RunBudgetState> stateOptional = RunBudgetState.from(ctx.getExtra());
        if (stateOptional.isEmpty() || ctx.hasForceFinishRequest()) {
            return;
        }
        RunBudgetState state = stateOptional.get();
        if (state.isTerminated()) {
            return;
        }
        if (ctx.getException() == null && modelResponse(ctx).isPresent()) {
            state.incrementRoundCount();
        }
        if (state.isTimeEnabled() && state.remainingSeconds() <= 0) {
            terminateForTimeBudget(ctx, state, RunBudgetRail.TERMINATED_AT_MODEL_CALL);
            return;
        }
        if (state.isTurnEnabled()) {
            evaluateTurnBudget(ctx, state);
        }
    }

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        Optional<RunBudgetState> stateOptional = RunBudgetState.from(ctx.getExtra());
        if (stateOptional.isEmpty() || ctx.hasForceFinishRequest()) {
            return;
        }
        RunBudgetState state = stateOptional.get();
        if (state.isTimeEnabled() && !state.isTerminated() && state.remainingSeconds() <= 0) {
            terminateForTimeBudget(ctx, state, RunBudgetRail.TERMINATED_AT_TOOL_DISPATCH);
        }
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        Optional<RunBudgetState> stateOptional = RunBudgetState.from(ctx.getExtra());
        if (stateOptional.isEmpty() || stateOptional.get().isTerminated()) {
            return;
        }
        RunBudgetState state = stateOptional.get();
        state.markNewToolResult();
        // A tool wait clamped to the remaining budget dies right at the deadline; detect that
        // here so the termination is staged before the agent's fail-fast-on-tool-error pass
        // (which yields to any pending force-finish request) and surfaces as tool_dispatch.
        if (state.isTimeEnabled() && !ctx.hasForceFinishRequest() && state.remainingSeconds() <= 0) {
            terminateForTimeBudget(ctx, state, RunBudgetRail.TERMINATED_AT_TOOL_DISPATCH);
        }
    }

    @Override
    public void onModelException(AgentCallbackContext ctx) {
        Optional<RunBudgetState> stateOptional = RunBudgetState.from(ctx.getExtra());
        if (stateOptional.isEmpty() || ctx.hasForceFinishRequest()) {
            return;
        }
        RunBudgetState state = stateOptional.get();
        if (!state.isTimeEnabled()) {
            return;
        }
        RetryRequest retryRequest = ctx.consumeRetryRequest();
        double remaining = state.remainingSeconds();
        if (remaining <= 0) {
            // Force the expiry checkpoint: an immediate retry makes the loop re-fire the
            // before hooks, where beforeModelCall stages the clean budget termination.
            // Without this, a model call that dies at the clamped deadline would propagate
            // its transport exception and override the budget termination surface.
            ctx.requestRetry(0.0D);
            return;
        }
        if (retryRequest != null) {
            ctx.requestRetry(Math.min(retryRequest.getDelaySeconds(), remaining));
        }
    }

    private void evaluateTurnBudget(AgentCallbackContext ctx, RunBudgetState state) {
        int round = state.getRoundCount();
        int hardLimit = state.getGuarantee().hardLimit();
        if (round >= hardLimit) {
            handleSafetyValveZone(ctx, state, round, hardLimit);
            return;
        }
        if (round < state.getFirstCheckpoint()) {
            return;
        }
        if (round == state.getNextCheckpoint()) {
            evaluateCheckpoint(ctx, state, round);
        }
        maybeGentleReminder(ctx, state, round);
    }

    private void handleSafetyValveZone(AgentCallbackContext ctx, RunBudgetState state, int round, int hardLimit) {
        boolean hasToolCalls = responseHasToolCalls(ctx);
        boolean loopWillContinue = hasToolCalls || ctx.hasPendingSteering();
        if (round == hardLimit && !state.isSafetyValveTriggered() && loopWillContinue) {
            state.markSafetyValveTriggered();
            ctx.pushSteering(SAFETY_VALVE_INSTRUCTION);
            RunBudgetEvent event = newEvent(state, LogEventType.TURN_BUDGET_SAFETY_VALVE_TRIGGERED);
            event.setRound(round);
            event.setHardLimit(hardLimit);
            event.setFinalRound(hardLimit + 1);
            event.setInstructionInjected(Boolean.TRUE);
            RunBudgetEvents.emit("run budget safety valve triggered",
                    LogEventType.TURN_BUDGET_SAFETY_VALVE_TRIGGERED, event);
            return;
        }
        // Defense in depth: >= so a host that registers this rail without the factory's
        // maxIterations uplift still terminates past the final round instead of silently
        // degrading to the plain iteration cut-off (markTerminated makes it single-shot).
        if (round >= hardLimit + 1 && hasToolCalls && state.markTerminated()) {
            ctx.requestForceFinish(buildTurnTerminatedResult(state, round));
            RunBudgetEvent event = newEvent(state, LogEventType.TURN_BUDGET_TERMINATED);
            event.setRound(round);
            event.setHardLimit(hardLimit);
            event.setReason(REASON_TURN_EXHAUSTED);
            event.setLogLevel(LogLevel.WARNING);
            RunBudgetEvents.emit("run budget terminated at the hard limit",
                    LogEventType.TURN_BUDGET_TERMINATED, event);
        }
    }

    private void evaluateCheckpoint(AgentCallbackContext ctx, RunBudgetState state, int round) {
        boolean hostStall = state.hasHostStallReport();
        boolean progressed = !hostStall && hasLocalProgress(ctx, state);
        Optional<RunBudgetState.HostStallReport> report = progressed
                ? Optional.empty() : state.consumeHostStallReport();
        if (progressed) {
            state.recordProgress(config.getProgressIntervalStep(), config.getMaxCheckpointInterval(), round);
        } else {
            state.recordStall(config.getStagnationEscalationThreshold(), config.getProgressIntervalStep(), round);
        }
        emitCheckpointEvent(state, round, progressed, hostStall);
        if (progressed) {
            RunBudgetEvent extended = newEvent(state, LogEventType.TURN_BUDGET_EXTENDED);
            extended.setRound(round);
            extended.setAllowedRounds(state.getNextCheckpoint());
            extended.setNextInterval(state.getNextCheckpoint() - round);
            RunBudgetEvents.emit("run budget extended after progress", LogEventType.TURN_BUDGET_EXTENDED,
                    extended);
        } else {
            injectStallPrompt(ctx, state, round, report);
        }
        state.startNewWindow(currentContent(ctx));
    }

    private void emitCheckpointEvent(RunBudgetState state, int round, boolean progressed, boolean hostStall) {
        RunBudgetEvent event = newEvent(state, LogEventType.TURN_BUDGET_CHECKPOINT);
        event.setRound(round);
        event.setCheckpoint(round);
        event.setProgressed(progressed);
        event.setConsecutiveStalls(state.getConsecutiveStalls());
        event.setAllowedRounds(state.getNextCheckpoint());
        event.setNextInterval(state.getNextCheckpoint() - round);
        event.setHostStallReported(hostStall);
        RunBudgetEvents.emit("run budget checkpoint evaluated", LogEventType.TURN_BUDGET_CHECKPOINT, event);
    }

    private void injectStallPrompt(AgentCallbackContext ctx, RunBudgetState state, int round,
                                   Optional<RunBudgetState.HostStallReport> report) {
        String prompt = defaultStallPrompt(state);
        if (report.isPresent() && report.get().promptText() != null && !report.get().promptText().isBlank()) {
            prompt = report.get().promptText();
        }
        ctx.pushSteering(prompt);
        RunBudgetEvent event = newEvent(state, LogEventType.TURN_BUDGET_STALL_PROMPTED);
        event.setRound(round);
        event.setConsecutiveStalls(state.getConsecutiveStalls());
        event.setEscalated(state.isEscalated());
        event.setSource(report.isPresent() ? "host" : "core");
        RunBudgetEvents.emit("run budget stall prompt injected", LogEventType.TURN_BUDGET_STALL_PROMPTED, event);
    }

    private String defaultStallPrompt(RunBudgetState state) {
        return state.isEscalated() ? STALL_PROMPT_ESCALATED : STALL_PROMPT;
    }

    private void maybeGentleReminder(AgentCallbackContext ctx, RunBudgetState state, int round) {
        int startAfter = state.getGuarantee().guaranteedRounds() * config.getGentleReminderStartMultiplier();
        if (round <= startAfter) {
            return;
        }
        if (round - state.getLastGentleReminderRound() < config.getGentleReminderInterval()) {
            return;
        }
        state.markGentleReminder(round);
        ctx.pushSteering(GENTLE_REMINDER);
        RunBudgetEvent event = newEvent(state, LogEventType.TURN_BUDGET_GENTLE_REMINDER);
        event.setRound(round);
        event.setReminderInterval(config.getGentleReminderInterval());
        RunBudgetEvents.emit("run budget gentle reminder injected", LogEventType.TURN_BUDGET_GENTLE_REMINDER,
                event);
    }

    private void maybePromptNearDeadline(AgentCallbackContext ctx, RunBudgetState state, double remaining) {
        if (state.isNearDeadlinePromptSent() || remaining >= state.getNearDeadlineThresholdSeconds()) {
            return;
        }
        state.markNearDeadlinePromptSent();
        ctx.pushSteering(NEAR_DEADLINE_PROMPT);
        RunBudgetEvent event = newEvent(state, LogEventType.TIME_BUDGET_NEAR_DEADLINE_PROMPTED);
        event.setRemainingSeconds(remaining);
        event.setThresholdSeconds(state.getNearDeadlineThresholdSeconds());
        RunBudgetEvents.emit("time budget near-deadline prompt injected",
                LogEventType.TIME_BUDGET_NEAR_DEADLINE_PROMPTED, event);
    }

    private void terminateForTimeBudget(AgentCallbackContext ctx, RunBudgetState state, String terminatedAt) {
        if (!state.markTerminated()) {
            return;
        }
        // Order matters: stage the termination first so a failing event emission (whose
        // exception the callback framework swallows) can never swallow the termination itself.
        // Context persistence is handled by the framework's force-finish branches on both legs.
        ctx.requestForceFinish(buildTimeTerminatedResult(state, terminatedAt));
        RunBudgetEvent event = newEvent(state, LogEventType.TIME_BUDGET_TERMINATED);
        event.setReason(REASON_TIME_EXHAUSTED);
        event.setTotalBudgetSeconds(state.getTotalBudgetSeconds());
        event.setElapsedSeconds(state.elapsedSeconds());
        event.setTerminatedAt(terminatedAt);
        event.setLogLevel(LogLevel.WARNING);
        RunBudgetEvents.emit("time budget exhausted; task terminated", LogEventType.TIME_BUDGET_TERMINATED,
                event);
    }

    private boolean hasLocalProgress(AgentCallbackContext ctx, RunBudgetState state) {
        if (state.hasNewToolResultSinceCheckpoint()) {
            return true;
        }
        String content = currentContent(ctx);
        return !content.isEmpty() && !content.equals(state.getLastModelContent());
    }

    private static String currentContent(AgentCallbackContext ctx) {
        Optional<Object> response = modelResponse(ctx);
        if (response.isPresent() && response.get() instanceof AssistantMessage assistantMessage
                && assistantMessage.getContent() != null) {
            return String.valueOf(assistantMessage.getContent());
        }
        return "";
    }

    private static Optional<Object> modelResponse(AgentCallbackContext ctx) {
        if (ctx.getInputs() instanceof ModelCallInputs modelCallInputs) {
            return Optional.ofNullable(modelCallInputs.getResponse());
        }
        return Optional.empty();
    }

    private static boolean responseHasToolCalls(AgentCallbackContext ctx) {
        Optional<Object> response = modelResponse(ctx);
        return response.isPresent() && response.get() instanceof AssistantMessage assistantMessage
                && assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty();
    }

    private void emitGuaranteeEffective(RunBudgetState state) {
        if (!state.isTurnEnabled()) {
            return;
        }
        RunBudgetEvent event = newEvent(state, LogEventType.TURN_BUDGET_GUARANTEE_EFFECTIVE);
        event.setGuaranteedRounds(state.getGuarantee().guaranteedRounds());
        event.setSource(state.getGuarantee().source());
        event.setHardLimit(state.getGuarantee().hardLimit());
        RunBudgetEvents.emit("run budget guarantee effective", LogEventType.TURN_BUDGET_GUARANTEE_EFFECTIVE,
                event);
    }

    private void emitTimeBudgetEffective(RunBudgetState state) {
        if (!state.isTimeEnabled()) {
            return;
        }
        RunBudgetEvent event = newEvent(state, LogEventType.TIME_BUDGET_EFFECTIVE);
        event.setTotalBudgetSeconds(state.getTotalBudgetSeconds());
        event.setSource(GuaranteeDecision.SOURCE_CONFIGURED);
        event.setNearDeadlineThresholdSeconds(state.getNearDeadlineThresholdSeconds());
        RunBudgetEvents.emit("time budget effective", LogEventType.TIME_BUDGET_EFFECTIVE, event);
    }

    private static RunBudgetEvent newEvent(RunBudgetState state, LogEventType type) {
        RunBudgetEvent event = new RunBudgetEvent(type);
        event.setConversationId(state.getConversationId());
        event.setSessionId(state.getSessionId());
        event.setAgentName(state.getAgentName());
        event.setAgentId(state.getAgentId());
        return event;
    }

    private static Map<String, Object> buildTurnTerminatedResult(RunBudgetState state, int round) {
        Map<String, Object> turnBudget = new LinkedHashMap<>();
        turnBudget.put("reason", REASON_TURN_EXHAUSTED);
        turnBudget.put("round", round);
        turnBudget.put("hard_limit", state.getGuarantee().hardLimit());
        turnBudget.put("guaranteed_rounds", state.getGuarantee().guaranteedRounds());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", TURN_TERMINATED_OUTPUT);
        result.put("result_type", "error");
        result.put("turn_budget", turnBudget);
        return result;
    }

    private static Map<String, Object> buildTimeTerminatedResult(RunBudgetState state, String terminatedAt) {
        Map<String, Object> timeBudget = new LinkedHashMap<>();
        timeBudget.put("reason", REASON_TIME_EXHAUSTED);
        timeBudget.put("total_budget_seconds", state.getTotalBudgetSeconds());
        timeBudget.put("elapsed_seconds", state.elapsedSeconds());
        timeBudget.put("terminated_at", terminatedAt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", TIME_TERMINATED_OUTPUT);
        result.put("result_type", "error");
        result.put("time_budget", timeBudget);
        return result;
    }
}
