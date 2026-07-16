/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.schema.status.ExecutionStatus;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
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
 * <p>
 * Scope intentionally stays small: task_failed chunk detection, retry-on-transient-stream-error,
 * interrupt-resume validation, pending-input drainage, and mailbox wakeup callback after a round.
 * Full TeamAgent runtime orchestration remains out of scope.
 * 
 * @since 0.1.7
 */
public class StreamController {
    /**
     * MAX_RETRY_ATTEMPTS.
     * 
     * @since 0.1.7
     */
    public static final int MAX_RETRY_ATTEMPTS = 10;

    /**
     * RETRYABLE_ERROR_CODES.
     * 
     * @since 0.1.7
     */
    public static final Set<Integer> RETRYABLE_ERROR_CODES = Set.of(181001);

    /**
     * RETRY_QUERY.
     * 
     * @since 0.1.7
     */
    public static final String RETRY_QUERY = "刚才有异常状况，继续执行";

    /**
     * TASK_FAILED_PAYLOAD_TYPE.
     * 
     * @since 0.1.7
     */
    public static final String TASK_FAILED_PAYLOAD_TYPE = "task_failed";

    /**
     * STREAM_END.
     * 
     * @since 0.1.7
     */
    public static final Object STREAM_END = new Object();

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("^\\[(\\d+)]");

    private final Supplier<Object> deepAgentGetter;
    private final Supplier<String> memberNameGetter;
    private final Consumer<MemberStatus> statusUpdater;
    private final Consumer<ExecutionStatus> executionUpdater;
    private final Supplier<Session> sessionGetter;
    private final Runnable wakeMailboxCallback;
    private final StreamRoundExecutor roundExecutor;

    /**
     * ArrayDeque<>.
     * 
     * @since 0.1.7
     */
    private final Queue<InteractiveInput> pendingInterruptResumes = new ArrayDeque<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Object> pendingInputs = new ArrayList<>();

    /**
     * LinkedBlockingQueue<>.
     * 
     * @since 0.1.7
     */
    private LinkedBlockingQueue<Object> streamQueue = new LinkedBlockingQueue<>();
    private boolean isStreamingActiveLegacy;
    private boolean streamingActive;
    private boolean isInFlightRound;
    private boolean isCloseStreamAfterCurrentRound;

    /**
     * StreamController.
     * 
     * @param deepAgentGetter deepAgentGetter
     * @param memberNameGetter memberNameGetter
     * @param statusUpdater statusUpdater
     * @param executionUpdater executionUpdater
     * @param sessionGetter sessionGetter
     * @since 0.1.7
     */
    public StreamController(Supplier<Object> deepAgentGetter, Supplier<String> memberNameGetter,
            Consumer<MemberStatus> statusUpdater, Consumer<ExecutionStatus> executionUpdater,
            Supplier<Session> sessionGetter) {
        this(deepAgentGetter, memberNameGetter, statusUpdater, executionUpdater, sessionGetter, null,
                defaultExecutor());
    }

    /**
     * StreamController.
     * 
     * @param deepAgentGetter deepAgentGetter
     * @param memberNameGetter memberNameGetter
     * @param statusUpdater statusUpdater
     * @param executionUpdater executionUpdater
     * @param sessionGetter sessionGetter
     * @param wakeMailboxCallback wakeMailboxCallback
     * @param roundExecutor roundExecutor
     * @since 0.1.7
     */
    public StreamController(Supplier<Object> deepAgentGetter, Supplier<String> memberNameGetter,
            Consumer<MemberStatus> statusUpdater, Consumer<ExecutionStatus> executionUpdater,
            Supplier<Session> sessionGetter, Runnable wakeMailboxCallback, StreamRoundExecutor roundExecutor) {
        this.deepAgentGetter = deepAgentGetter != null ? deepAgentGetter : () -> null;
        this.memberNameGetter = memberNameGetter != null ? memberNameGetter : () -> null;
        this.statusUpdater = statusUpdater != null ? statusUpdater : ignored -> {
        };
        this.executionUpdater = executionUpdater != null ? executionUpdater : ignored -> {
        };
        this.sessionGetter = sessionGetter != null ? sessionGetter : () -> null;
        this.wakeMailboxCallback = wakeMailboxCallback;
        this.roundExecutor = roundExecutor != null ? roundExecutor : defaultExecutor();
    }

    /**
     * isAgentRunning.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isAgentRunning() {
        return isStreamingActiveLegacy || streamingActive;
    }

    /**
     * hasInFlightRound.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasInFlightRound() {
        return isInFlightRound;
    }

    /**
     * getPendingInterruptResumes.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Queue<InteractiveInput> getPendingInterruptResumes() {
        return pendingInterruptResumes;
    }

    /**
     * getPendingInputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Object> getPendingInputs() {
        return pendingInputs;
    }

    /**
     * getStreamQueue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LinkedBlockingQueue<Object> getStreamQueue() {
        return streamQueue;
    }

    /**
     * setStreamQueue.
     * 
     * @param streamQueue streamQueue
     * @since 0.1.7
     */
    public void setStreamQueue(LinkedBlockingQueue<Object> streamQueue) {
        this.streamQueue = streamQueue != null ? streamQueue : new LinkedBlockingQueue<>();
    }

    /**
     * requestCloseStreamAfterCurrentRound.
     * 
     * @since 0.1.7
     */
    public void requestCloseStreamAfterCurrentRound() {
        isCloseStreamAfterCurrentRound = true;
    }

    /**
     * startRound.
     * 
     * @param content content
     * @since 0.1.7
     */
    public void startRound(Object content) {
        if (deepAgentGetter.get() == null || streamQueue == null) {
            return;
        }
        runOneRound(content);
    }

    /**
     * steer.
     * 
     * @param content content
     * @since 0.1.7
     */
    public void steer(Object content) {
        Object deepAgent = deepAgentGetter.get();
        if (deepAgent instanceof DeepAgent agent) {
            agent.steer(stringify(content), resolveAgentSessionApi());
        }
    }

    /**
     * isFollowUp.
     * 
     * @param content content
     * @since 0.1.7
     */
    public void isFollowUp(Object content) {
        Object deepAgent = deepAgentGetter.get();
        if (deepAgent instanceof DeepAgent agent) {
            agent.isFollowUp(stringify(content), resolveAgentSessionApi());
        }
    }

    /**
     * cancelAgent.
     * 
     * @since 0.1.7
     */
    public void cancelAgent() {
        executionUpdater.accept(ExecutionStatus.CANCEL_REQUESTED);
        executionUpdater.accept(ExecutionStatus.CANCELLING);
        executionUpdater.accept(ExecutionStatus.CANCELLED);
        executionUpdater.accept(ExecutionStatus.IDLE);
        isInFlightRound = false;
        streamingActive = false;
        isStreamingActiveLegacy = false;
    }

    /**
     * closeStream.
     * 
     * @since 0.1.7
     */
    public void closeStream() {
        if (streamQueue != null) {
            streamQueue.offer(STREAM_END);
        }
    }

    /**
     * hasPendingInterrupt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasPendingInterrupt() {
        return pendingInterruptIds().size() > 0;
    }

    /**
     * isValidInterruptResume.
     * 
     * @param userInput userInput
     * @return the result
     * @since 0.1.7
     */
    public boolean isValidInterruptResume(Object userInput) {
        if (!(userInput instanceof InteractiveInput interactiveInput)) {
            return false;
        }
        Set<String> pendingIds = pendingInterruptIds();
        if (pendingIds.isEmpty() || interactiveInput.getUserInputs() == null
                || interactiveInput.getUserInputs().isEmpty()) {
            return false;
        }
        return pendingIds.containsAll(interactiveInput.getUserInputs().keySet());
    }

    /**
     * dequeueValidInterruptResume.
     * 
     * @return the result
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
     * runOneRound.
     * 
     * @param message message
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
            statusUpdater.accept(MemberStatus.ERROR);
            throw runtimeException;
        } finally {
            isInFlightRound = false;
            InteractiveInput nextResume = dequeueValidInterruptResume();
            if (nextResume != null) {
                startRound(nextResume);
            } else if (!pendingInputs.isEmpty()) {
                List<Object> drained = new ArrayList<>(pendingInputs);
                pendingInputs.clear();
                startRound(combinePendingInputs(drained));
            } else if (wakeMailboxCallback != null) {
                wakeMailboxCallback.run();
            } else {
                // no-op
            }
            if (isCloseStreamAfterCurrentRound) {
                isCloseStreamAfterCurrentRound = false;
                closeStream();
            }
        }
    }

    /**
     * executeRound.
     * 
     * @param message message
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
            if (runtimeException.getCause() instanceof TimeoutException) {
                executionUpdater.accept(ExecutionStatus.TIMED_OUT);
            } else {
                executionUpdater.accept(ExecutionStatus.FAILED);
            }
            throw runtimeException;
        } finally {
            executionUpdater.accept(ExecutionStatus.IDLE);
            streamingActive = false;
            isStreamingActiveLegacy = false;
        }
    }

    /**
     * runRetryingStream.
     * 
     * @param initialQuery initialQuery
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
            if (failure.code() != null && RETRYABLE_ERROR_CODES.contains(failure.code())
                    && attempt < MAX_RETRY_ATTEMPTS) {
                attempt += 1;
                currentQuery = RETRY_QUERY;
                continue;
            }
            throw buildStreamingTaskFailed(attempt, failure);
        }
    }

    /**
     * streamOneRound.
     * 
     * @param query query
     * @return the result
     * @since 0.1.7
     */
    public TaskFailure streamOneRound(Object query) {
        Object deepAgent = deepAgentGetter.get();
        if (deepAgent == null) {
            return nullValue();
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", query);
        streamingActive = true;
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
     * detectTaskFailed.
     * 
     * @param chunk chunk
     * @return the result
     * @since 0.1.7
     */
    public static TaskFailure detectTaskFailed(Object chunk) {
        ControllerOutputPayload payload = extractPayload(chunk);
        if (payload == null || !TASK_FAILED_PAYLOAD_TYPE.equals(payload.getType())) {
            return nullValue();
        }
        String text = "";
        List<DataFrame> data = payload.getData();
        if (data != null && !data.isEmpty() && data.get(0) instanceof DataFrame.TextDataFrame textDataFrame) {
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

    /**
     * resolveSessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    private String resolveSessionId() {
        Session session = sessionGetter.get();
        return session != null ? session.getSessionId() : null;
    }

    /**
     * resolveAgentSessionApi.
     * 
     * @return the result
     * @since 0.1.7
     */
    private AgentSessionApi resolveAgentSessionApi() {
        Session session = sessionGetter.get();
        return session instanceof AgentSessionApi agentSessionApi ? agentSessionApi : null;
    }

    /**
     * pendingInterruptIds.
     * 
     * @return the result
     * @since 0.1.7
     */
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
        return interruptionState.getInterruptedTools().stream().map(StreamController::resolveInterruptId)
                .filter(Objects::nonNull).filter(id -> !id.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * resolveInterruptId.
     * 
     * @param entry entry
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * combinePendingInputs.
     * 
     * @param drained drained
     * @return the result
     * @since 0.1.7
     */
    private static String combinePendingInputs(List<Object> drained) {
        if (drained.size() == 1) {
            return stringify(drained.get(0));
        }
        return drained.stream().map(StreamController::stringify).reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("");
    }

    /**
     * stringify.
     * 
     * @param item item
     * @return the result
     * @since 0.1.7
     */
    private static String stringify(Object item) {
        return item instanceof String text ? text : String.valueOf(item);
    }

    /**
     * extractPayload.
     * 
     * @param chunk chunk
     * @return the result
     * @since 0.1.7
     */
    private static ControllerOutputPayload extractPayload(Object chunk) {
        if (chunk instanceof ControllerOutputChunk controllerOutputChunk) {
            return controllerOutputChunk.getControllerPayload();
        }
        return nullValue();
    }

    /**
     * buildStreamingTaskFailed.
     * 
     * @param attempt attempt
     * @param failure failure
     * @return the result
     * @since 0.1.7
     */
    private static BaseError buildStreamingTaskFailed(int attempt, TaskFailure failure) {
        String reason = "streaming task failed after " + attempt + " retries, last error code=" + failure.code() + ": "
                + failure.text();
        return ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_EXECUTION_CALL_FAILED, "error_msg", reason);
    }

    /**
     * defaultExecutor.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static StreamRoundExecutor defaultExecutor() {
        return (deepAgent, inputs, sessionId) -> Runner.runAgentStreaming(deepAgent, inputs, sessionId, null, null);
    }

    /**
     * Public interface StreamRoundExecutor used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface StreamRoundExecutor {
        /**
         * run.
         * 
         * @param deepAgent deepAgent
         * @param inputs inputs
         * @param sessionId sessionId
         * @return the result
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
    }

    /**
     * nullValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static <T> T nullValue() {
        return null;
    }
}
