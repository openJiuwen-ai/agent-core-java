/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.schema.status.ExecutionStatus;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptEntry;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Narrow Java port of the Python agent_teams stream-controller helper slice.
 *
 * <p>Scope intentionally stays small: task_failed chunk detection, retry-on-transient-stream-error,
 * interrupt-resume validation, pending-input drainage, and mailbox wakeup callback after a round.
 * Full TeamAgent runtime orchestration remains out of scope.
 *
 * @since 2026/7/9
 */
public class StreamController {
    /**
     * Maximum retry attempts for transient stream errors.
     *
     * @since 0.1.7
     */
    public static final int MAX_RETRY_ATTEMPTS = 10;

    /**
     * Error codes that should trigger a retry of the streaming round.
     *
     * @since 0.1.7
     */
    public static final Set<Integer> RETRYABLE_ERROR_CODES = Set.of(181001);

    /**
     * Query string used to resume the agent after a transient error.
     *
     * @since 0.1.7
     */
    public static final String RETRY_QUERY = "刚才有异常状况，继续执行";

    /**
     * Payload type used to identify a task-failed chunk emitted by the controller.
     *
     * @since 0.1.7
     */
    public static final String TASK_FAILED_PAYLOAD_TYPE = "task_failed";

    /**
     * Sentinel object enqueued onto the stream queue to signal end of stream.
     *
     * @since 0.1.7
     */
    public static final Object STREAM_END = new Object();

    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("^\\[(\\d+)]");

    private final Supplier<Object> deepAgentGetter;
    private final Supplier<String> memberNameGetter;
    private final Consumer<MemberStatus> statusUpdater;
    private final Consumer<ExecutionStatus> executionUpdater;
    private final Supplier<Session> sessionGetter;
    private final Runnable wakeMailboxCallback;
    private final StreamRoundExecutor roundExecutor;

    // Mirrors Python stream_controller._on_idle_settled: lets the round-end
    // finally block read the member's persisted status and close the stream
    // when SHUTDOWN_REQUESTED, so the runner drives finalize -> SHUTDOWN.
    private final Supplier<MemberStatus> statusGetter;

    private final Queue<InteractiveInput> pendingInterruptResumes = new ArrayDeque<>();
    private final List<Object> pendingInputs = new ArrayList<>();
    private LinkedBlockingQueue<Object> streamQueue = new LinkedBlockingQueue<>();
    private boolean isStreamingActiveLegacy;
    private boolean isStreamingActive;
    private boolean isInFlightRound;
    private boolean isCloseStreamAfterCurrentRound;

    /**
     * Latched true when the hosting team has been cleaned/deleted. Once set,
     * {@link #runOneRound(Object)} finally closes the stream and skips the
     * mailbox wake callback so the leader does not loop on stale dispatch
     * prompts (e.g. {@code all_done_temporary}) after {@code clean_team}.
     */
    private volatile boolean isTeamTerminated;

    /**
     * Construct a controller using the default round executor and no mailbox wake callback.
     *
     * @param deepAgentGetter supplier for the underlying deep agent
     * @param memberNameGetter supplier for the local member name
     * @param statusUpdater consumer that updates member status
     * @param executionUpdater consumer that updates execution status
     * @param sessionGetter supplier for the current session
     * @since 0.1.7
     */
    public StreamController(
            Supplier<Object> deepAgentGetter,
            Supplier<String> memberNameGetter,
            Consumer<MemberStatus> statusUpdater,
            Consumer<ExecutionStatus> executionUpdater,
            Supplier<Session> sessionGetter) {
        this(
                deepAgentGetter,
                memberNameGetter,
                statusUpdater,
                executionUpdater,
                sessionGetter,
                null,
                defaultExecutor(),
                null);
    }

    /**
     * Construct a controller with a custom round executor and mailbox wake callback but no status getter.
     *
     * @param deepAgentGetter supplier for the underlying deep agent
     * @param memberNameGetter supplier for the local member name
     * @param statusUpdater consumer that updates member status
     * @param executionUpdater consumer that updates execution status
     * @param sessionGetter supplier for the current session
     * @param wakeMailboxCallback callback run when a round ends with no pending input
     * @param roundExecutor executor that drives one streaming round
     * @since 0.1.7
     */
    public StreamController(
            Supplier<Object> deepAgentGetter,
            Supplier<String> memberNameGetter,
            Consumer<MemberStatus> statusUpdater,
            Consumer<ExecutionStatus> executionUpdater,
            Supplier<Session> sessionGetter,
            Runnable wakeMailboxCallback,
            StreamRoundExecutor roundExecutor) {
        this(
                deepAgentGetter,
                memberNameGetter,
                statusUpdater,
                executionUpdater,
                sessionGetter,
                wakeMailboxCallback,
                roundExecutor,
                null);
    }

    /**
     * Full constructor with status getter.
     *
     * <p>The {@code statusGetter} mirrors Python {@code stream_controller.py:
     * StreamController._on_idle_settled} reading the member's persisted status
     * at round-end to decide whether to close the stream (SHUTDOWN_REQUESTED)
     * so the runner can drive {@code finalize_member} -> SHUTDOWN.</p>
     *
     * @param deepAgentGetter supplier for the underlying deep agent;
     *     {@code null} yields a {@code null}-returning supplier
     * @param memberNameGetter supplier for the local member name; {@code null} yields a {@code null}-returning supplier
     * @param statusUpdater consumer that updates member status; {@code null} yields a no-op
     * @param executionUpdater consumer that updates execution status; {@code null} yields a no-op
     * @param sessionGetter supplier for the current session; {@code null} yields a {@code null}-returning supplier
     * @param wakeMailboxCallback callback run when a round ends with no pending input; may be {@code null}
     * @param roundExecutor executor that drives one streaming round; {@code null} falls back to default
     * @param statusGetter supplier for the persisted member status; may be {@code null}
     * @since 0.1.7
     */
    public StreamController(
            Supplier<Object> deepAgentGetter,
            Supplier<String> memberNameGetter,
            Consumer<MemberStatus> statusUpdater,
            Consumer<ExecutionStatus> executionUpdater,
            Supplier<Session> sessionGetter,
            Runnable wakeMailboxCallback,
            StreamRoundExecutor roundExecutor,
            Supplier<MemberStatus> statusGetter) {
        this.deepAgentGetter = deepAgentGetter != null ? deepAgentGetter : () -> null;
        this.memberNameGetter = memberNameGetter != null ? memberNameGetter : () -> null;
        this.statusUpdater = statusUpdater != null ? statusUpdater : ignored -> {};
        this.executionUpdater = executionUpdater != null ? executionUpdater : ignored -> {};
        this.sessionGetter = sessionGetter != null ? sessionGetter : () -> null;
        this.wakeMailboxCallback = wakeMailboxCallback;
        this.roundExecutor = roundExecutor != null ? roundExecutor : defaultExecutor();
        this.statusGetter = statusGetter;
    }

    /**
     * Check whether any streaming round is currently active.
     *
     * @return {@code true} if a round is in flight or streaming is active
     * @since 0.1.7
     */
    public boolean isAgentRunning() {
        return isStreamingActiveLegacy || isStreamingActive;
    }

    /**
     * Check whether a round is currently in flight.
     *
     * @return {@code true} while a round is executing
     * @since 0.1.7
     */
    public boolean hasInFlightRound() {
        return isInFlightRound;
    }

    /**
     * Get the queue of pending interrupt-resume inputs awaiting validation.
     *
     * @return the pending interrupt-resume queue
     * @since 0.1.7
     */
    public Queue<InteractiveInput> getPendingInterruptResumes() {
        return pendingInterruptResumes;
    }

    /**
     * Get the list of pending user inputs to be drained on the next round.
     *
     * @return the pending inputs list
     * @since 0.1.7
     */
    public List<Object> getPendingInputs() {
        return pendingInputs;
    }

    /**
     * Get the stream queue used to emit chunks to consumers.
     *
     * @return the stream queue
     * @since 0.1.7
     */
    public LinkedBlockingQueue<Object> getStreamQueue() {
        return streamQueue;
    }

    /**
     * Replace the stream queue; {@code null} resets to an empty queue.
     *
     * @param streamQueue the new stream queue; may be {@code null}
     * @since 0.1.7
     */
    public void setStreamQueue(LinkedBlockingQueue<Object> streamQueue) {
        this.streamQueue = streamQueue != null ? streamQueue : new LinkedBlockingQueue<>();
    }

    /**
     * Request that the stream be closed once the current round finishes.
     *
     * @since 0.1.7
     */
    public void requestCloseStreamAfterCurrentRound() {
        isCloseStreamAfterCurrentRound = true;
    }

    /**
     * Start a new streaming round with the given content unless the deep agent or queue is missing.
     *
     * @param content the input to feed into the round
     * @since 0.1.7
     */
    public void startRound(Object content) {
        if (isTeamTerminated) {
            Loggers.AGENT.info("StreamController.startRound: team terminated, refusing new round for member={}",
                    memberNameGetter != null ? memberNameGetter.get() : "null");
            return;
        }
        if (deepAgentGetter.get() == null || streamQueue == null) {
            return;
        }
        runOneRound(content);
    }

    /**
     * Steer the running deep agent with new content using its steer API.
     *
     * @param content the steer input
     * @since 0.1.7
     */
    public void steer(Object content) {
        Object deepAgent = deepAgentGetter.get();
        if (deepAgent instanceof DeepAgent agent) {
            agent.steer(stringify(content), resolveAgentSessionApi());
        }
    }

    /**
     * Ask the running deep agent whether the content is a follow-up to the previous exchange.
     *
     * @param content the content to test
     * @since 0.1.7
     */
    public void isFollowUp(Object content) {
        Object deepAgent = deepAgentGetter.get();
        if (deepAgent instanceof DeepAgent agent) {
            agent.isFollowUp(stringify(content), resolveAgentSessionApi());
        }
    }

    /**
     * Cancel the agent: drive execution status through
     * CANCEL_REQUESTED -> CANCELLING -> CANCELLED -> IDLE and reset round flags.
     *
     * @since 0.1.7
     */
    public void cancelAgent() {
        executionUpdater.accept(ExecutionStatus.CANCEL_REQUESTED);
        executionUpdater.accept(ExecutionStatus.CANCELLING);
        executionUpdater.accept(ExecutionStatus.CANCELLED);
        executionUpdater.accept(ExecutionStatus.IDLE);
        isInFlightRound = false;
        isStreamingActive = false;
        isStreamingActiveLegacy = false;
    }

    /**
     * Close the stream by enqueuing the {@link #STREAM_END} sentinel onto the stream queue.
     *
     * @since 0.1.7
     */
    public void closeStream() {
        if (streamQueue != null) {
            streamQueue.offer(STREAM_END);
        }
    }

    /**
     * Mark the hosting team as terminated. Subsequent {@link #runOneRound(Object)}
     * finally blocks close the stream and skip mailbox wakeup, preventing the
     * leader from looping on stale dispatch prompts after {@code clean_team}.
     *
     * @since 0.1.15
     */
    public void markTeamTerminated() {
        isTeamTerminated = true;
    }

    /**
     * Reset the team-terminated latch. Called when a new team is built on the
     * same TeamAgent so the StreamController can accept new rounds again.
     *
     * @since 0.1.15
     */
    public void resetTeamTerminated() {
        isTeamTerminated = false;
    }

    /**
     * Whether the hosting team has been marked terminated.
     *
     * @return {@code true} once {@link #markTeamTerminated()} has been called
     * @since 0.1.15
     */
    public boolean isTeamTerminated() {
        return isTeamTerminated;
    }

    /**
     * Check whether any tool interrupt is currently pending.
     *
     * @return {@code true} if at least one interrupt is pending
     * @since 0.1.7
     */
    public boolean hasPendingInterrupt() {
        return pendingInterruptIds().size() > 0;
    }

    /**
     * Check whether the supplied user input is a valid resume for the currently pending interrupts.
     *
     * @param userInput the candidate input; must be an {@link InteractiveInput}
     * @return {@code true} when the input resolves every pending interrupt id
     * @since 0.1.7
     */
    public boolean isValidInterruptResume(Object userInput) {
        if (!(userInput instanceof InteractiveInput interactiveInput)) {
            return false;
        }
        Set<String> pendingIds = pendingInterruptIds();
        if (pendingIds.isEmpty()
                || interactiveInput.getUserInputs() == null
                || interactiveInput.getUserInputs().isEmpty()) {
            return false;
        }
        return pendingIds.containsAll(interactiveInput.getUserInputs().keySet());
    }

    /**
     * Dequeue the next pending interrupt-resume input that is still valid against the active interrupts.
     *
     * @return the next valid resume, or {@code null} when none is available
     * @since 0.1.7
     */
    public InteractiveInput dequeueValidInterruptResume() {
        while (!pendingInterruptResumes.isEmpty()) {
            InteractiveInput candidate = pendingInterruptResumes.poll();
            if (isValidInterruptResume(candidate)) {
                return candidate;
            }
        }
        return nullValue();
    }

    /**
     * Run one full round: mark the member busy, execute the round,
     * then drain the next resume, pending inputs, or wake the mailbox.
     *
     * @param message the input to feed into the round
     * @since 0.1.7
     */
    public void runOneRound(Object message) {
        isInFlightRound = true;
        statusUpdater.accept(MemberStatus.READY);
        statusUpdater.accept(MemberStatus.BUSY);
        try {
            executeRound(message);
            statusUpdater.accept(MemberStatus.READY);
        } catch (RuntimeException runtimeException) {
            // Safety net: executeRound delegates to deep-agent code that can throw
            // various RuntimeExceptions (BaseError, TimeoutException cause, etc.);
            // catching broadly is intentional since the exception is re-thrown after
            // updating the member status to ERROR.
            statusUpdater.accept(MemberStatus.ERROR);
            throw runtimeException;
        } finally {
            isInFlightRound = false;
            handlePostRoundContinuation();
        }
    }

    /**
     * Handle post-round continuation: check for shutdown/termination, then
     * resume from interrupt, drain pending inputs, or wake the mailbox.
     *
     * <p>Mirrors Python stream_controller._on_idle_settled: when the round
     * chain ends and the member's persisted status is SHUTDOWN_REQUESTED,
     * close the stream so the runner drives finalize_member -&gt; SHUTDOWN.
     * Must run before mailbox wakeup so we don't enqueue another round.</p>
     */
    private void handlePostRoundContinuation() {
        MemberStatus currentStatus = statusGetter != null ? statusGetter.get() : null;
        Loggers.AGENT.info("StreamController.runOneRound finally: member={} status={}"
                + " isCloseStreamAfterCurrentRound={} pendingInputs={} hasWakeCallback={}",
                memberNameGetter != null ? memberNameGetter.get() : "null",
                currentStatus, isCloseStreamAfterCurrentRound,
                pendingInputs.size(), wakeMailboxCallback != null);
        if (currentStatus == MemberStatus.SHUTDOWN_REQUESTED) {
            // Latch isTeamTerminated so subsequent startRound() refuses new
            // rounds and deliverInput() drops queued POLL_MAILBOX events.
            // Without this, EventBus drains mailbox-sweep events that
            // re-trigger deliverInput -> runOneRound after closeStream,
            // relaunching the member on stale task assignments even though
            // the leader already asked for shutdown.
            isTeamTerminated = true;
            closeStream();
            return;
        }
        // When the member's DB record is gone (team cleaned / member deleted),
        // statusGetter returns null. Without this guard the member loops forever
        // on wakeMailboxCallback because neither SHUTDOWN_REQUESTED nor
        // isTeamTerminated matches. Treat null status as team-already-cleaned:
        // latch isTeamTerminated so subsequent startRound() refuses new rounds
        // (the EventBus keeps draining queued POLL_MAILBOX events that would
        // otherwise re-trigger deliverInput -> runOneRound), then close the
        // stream so invokeForSpawn breaks on STREAM_END.
        if (currentStatus == null) {
            Loggers.AGENT.info("StreamController.runOneRound finally: member status null"
                    + " (team cleaned?), marking terminated and closing stream for member={}",
                    memberNameGetter != null ? memberNameGetter.get() : "null");
            isTeamTerminated = true;
            closeStream();
            return;
        }
        if (isTeamTerminated) {
            Loggers.AGENT.info("StreamController.runOneRound finally: team terminated,"
                    + " closing stream for member={}",
                    memberNameGetter != null ? memberNameGetter.get() : "null");
            closeStream();
            return;
        }
        InteractiveInput nextResume = dequeueValidInterruptResume();
        if (nextResume != null) {
            Loggers.AGENT.info("StreamController.runOneRound finally: starting round from interruptResume");
            startRound(nextResume);
        } else if (!pendingInputs.isEmpty()) {
            List<Object> drained = new ArrayList<>(pendingInputs);
            pendingInputs.clear();
            Loggers.AGENT.info("StreamController.runOneRound finally: starting round"
                    + " from pendingInputs count={}", drained.size());
            startRound(combinePendingInputs(drained));
        } else if (wakeMailboxCallback != null) {
            Loggers.AGENT.info("StreamController.runOneRound finally: calling wakeMailboxCallback");
            wakeMailboxCallback.run();
        } else {
            Loggers.AGENT.debug("StreamController.runOneRound finally: no pending inputs or callback");
        }
        if (isCloseStreamAfterCurrentRound) {
            isCloseStreamAfterCurrentRound = false;
            Loggers.AGENT.info("StreamController.runOneRound finally: closing stream after current round");
            closeStream();
        }
    }

    /**
     * Execute a single round: walk execution status through
     * STARTING -> RUNNING -> (COMPLETING -> COMPLETED | TIMED_OUT/FAILED) -> IDLE.
     *
     * @param message the input to feed into the round
     * @since 0.1.7
     */
    public void executeRound(Object message) {
        executionUpdater.accept(ExecutionStatus.STARTING);
        executionUpdater.accept(ExecutionStatus.RUNNING);
        try {
            runRetryingStream(message);
            executionUpdater.accept(ExecutionStatus.COMPLETING);
            executionUpdater.accept(ExecutionStatus.COMPLETED);
        } catch (RuntimeException runtimeException) {
            // Safety net: runRetryingStream delegates to deep-agent code that can
            // throw various RuntimeExceptions; catching broadly is intentional since
            // the exception is re-thrown after updating execution status.
            if (runtimeException.getCause() instanceof TimeoutException) {
                executionUpdater.accept(ExecutionStatus.TIMED_OUT);
            } else {
                executionUpdater.accept(ExecutionStatus.FAILED);
            }
            throw runtimeException;
        } finally {
            executionUpdater.accept(ExecutionStatus.IDLE);
            isStreamingActive = false;
            isStreamingActiveLegacy = false;
        }
    }

    /**
     * Run the streaming round, retrying with a resume query when a transient retryable error is reported.
     *
     * @param initialQuery the first input to feed into the round
     * @since 0.1.7
     */
    public void runRetryingStream(Object initialQuery) {
        Object currentQuery = initialQuery;
        int attempt = 0;
        while (true) {
            TaskFailure failure = streamOneRound(currentQuery);
            if (failure == null) {
                return;
            }
            if (failure.code() != null
                    && RETRYABLE_ERROR_CODES.contains(failure.code())
                    && attempt < MAX_RETRY_ATTEMPTS) {
                attempt += 1;
                currentQuery = RETRY_QUERY;
                continue;
            }
            throw buildStreamingTaskFailed(attempt, failure);
        }
    }

    /**
     * Drive one streaming round through the executor and surface any task-failure detected in emitted chunks.
     *
     * @param query the input to feed into the round
     * @return the detected {@link TaskFailure}, or {@code null} when the round completed without a task-failed chunk
     * @since 0.1.7
     */
    public TaskFailure streamOneRound(Object query) {
        Object deepAgent = deepAgentGetter.get();
        if (deepAgent == null) {
            return nullValue();
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", query);
        isStreamingActive = true;
        isStreamingActiveLegacy = true;

        Iterator<Object> iterator = roundExecutor.run(deepAgent, inputs, resolveSessionId());
        boolean isErrorSeen = false;
        TaskFailure failure = null;
        while (iterator != null && iterator.hasNext()) {
            Object chunk = iterator.next();
            if (isErrorSeen) {
                continue;
            }
            TaskFailure detected = detectTaskFailed(chunk);
            if (detected != null) {
                isErrorSeen = true;
                failure = detected;
                continue;
            }
            if (streamQueue != null) {
                streamQueue.offer(chunk);
            }
        }
        return failure;
    }

    /**
     * Detect a {@code task_failed} payload in a chunk and extract the optional error code and text.
     *
     * @param chunk the chunk emitted by the controller
     * @return the detected {@link TaskFailure}, or {@code null} when the chunk is not a task-failed payload
     * @since 0.1.7
     */
    public static TaskFailure detectTaskFailed(Object chunk) {
        ControllerOutputPayload payload = extractPayload(chunk);
        if (payload == null || !TASK_FAILED_PAYLOAD_TYPE.equals(payload.getType())) {
            return nullValue();
        }
        String text = "";
        List<DataFrame> data = payload.getData();
        if (data != null
                && !data.isEmpty()
                && data.get(0) instanceof DataFrame.TextDataFrame textDataFrame) {
            text = Objects.toString(textDataFrame.text(), "");
        }
        Integer code = null;
        Matcher matcher = ERROR_CODE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                code = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                code = null;
            }
        }
        return new TaskFailure(code, text);
    }

    private String resolveSessionId() {
        Session session = sessionGetter.get();
        return session != null ? session.getSessionId() : null;
    }

    private AgentSessionApi resolveAgentSessionApi() {
        Session session = sessionGetter.get();
        return session instanceof AgentSessionApi agentSessionApi ? agentSessionApi : null;
    }

    private Set<String> pendingInterruptIds() {
        Session session = sessionGetter.get();
        if (session == null) {
            return Set.of();
        }
        Object state = session.getState(ToolInterruptionState.INTERRUPTION_KEY);
        if (!(state instanceof ToolInterruptionState interruptionState)
                || interruptionState.getInterruptedTools() == null
                || interruptionState.getInterruptedTools().isEmpty()) {
            return Set.of();
        }
        return interruptionState.getInterruptedTools().stream()
                .map(StreamController::resolveInterruptId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static String resolveInterruptId(ToolInterruptEntry entry) {
        if (entry == null) {
            return nullValue();
        }
        if (entry.getRequest() != null && entry.getRequest().getInterruptId() != null) {
            return entry.getRequest().getInterruptId();
        }
        if (entry.getToolCall() != null) {
            return entry.getToolCall().getId();
        }
        return nullValue();
    }

    private static String combinePendingInputs(List<Object> drained) {
        if (drained.size() == 1) {
            return stringify(drained.get(0));
        }
        return drained.stream()
                .map(StreamController::stringify)
                .reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("");
    }

    private static String stringify(Object item) {
        return item instanceof String text ? text : String.valueOf(item);
    }

    private static ControllerOutputPayload extractPayload(Object chunk) {
        if (chunk instanceof ControllerOutputChunk controllerOutputChunk) {
            return controllerOutputChunk.getControllerPayload();
        }
        return nullValue();
    }

    private static BaseError buildStreamingTaskFailed(int attempt, TaskFailure failure) {
        String reason =
                "streaming task failed after "
                        + attempt
                        + " retries, last error code="
                        + failure.code()
                        + ": "
                        + failure.text();
        return ErrorHelper.buildError(
                StatusCode.AGENT_CONTROLLER_EXECUTION_CALL_FAILED, "error_msg", reason);
    }

    private static StreamRoundExecutor defaultExecutor() {
        return (deepAgent, inputs, sessionId) ->
                Runner.runAgentStreaming(deepAgent, inputs, sessionId, null, null);
    }

    /**
     * Functional interface that drives one streaming round against a deep agent.
     *
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface StreamRoundExecutor {
        /**
         * Run a streaming round and return an iterator over emitted chunks.
         *
         * @param deepAgent the deep agent to drive
         * @param inputs the inputs map; typically contains a {@code query} entry
         * @param sessionId the session id for the round; may be {@code null}
         * @return an iterator over emitted chunks
         * @since 0.1.7
         */
        Iterator<Object> run(Object deepAgent, Map<String, Object> inputs, String sessionId);
    }

    /**
     * Public record TaskFailure used by the Java parity implementation.
     *
     * @since 0.1.7
     */
    public record TaskFailure(Integer code, String text) {
        // empty: failure tuple; accessors generated by compiler
    }

    private static <T> T nullValue() {
        return null;
    }
}
