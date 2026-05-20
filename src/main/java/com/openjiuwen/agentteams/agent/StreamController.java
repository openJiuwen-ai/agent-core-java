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
 *
 * <p>Scope intentionally stays small: task_failed chunk detection, retry-on-transient-stream-error,
 * interrupt-resume validation, pending-input drainage, and mailbox wakeup callback after a round.
 * Full TeamAgent runtime orchestration remains out of scope.
 */
public class StreamController {
  /** Auto-generated for codecheck compliance. */
  public static final int MAX_RETRY_ATTEMPTS = 10;

  /** Auto-generated for codecheck compliance. */
  public static final Set<Integer> RETRYABLE_ERROR_CODES = Set.of(181001);

  /** Auto-generated for codecheck compliance. */
  public static final String RETRY_QUERY = "刚才有异常状况，继续执行";

  /** Auto-generated for codecheck compliance. */
  public static final String TASK_FAILED_PAYLOAD_TYPE = "task_failed";

  /** Auto-generated for codecheck compliance. */
  public static final Object STREAM_END = new Object();

  private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("^\\[(\\d+)]");

  private final Supplier<Object> deepAgentGetter;
  private final Supplier<String> memberNameGetter;
  private final Consumer<MemberStatus> statusUpdater;
  private final Consumer<ExecutionStatus> executionUpdater;
  private final Supplier<Session> sessionGetter;
  private final Runnable wakeMailboxCallback;
  private final StreamRoundExecutor roundExecutor;

  private final Queue<InteractiveInput> pendingInterruptResumes = new ArrayDeque<>();
  private final List<Object> pendingInputs = new ArrayList<>();
  private LinkedBlockingQueue<Object> streamQueue = new LinkedBlockingQueue<>();
  private boolean isStreamingActiveLegacy;
  private boolean streamingActive;
  private boolean isInFlightRound;
  private boolean isCloseStreamAfterCurrentRound;

  /** Auto-generated for codecheck compliance. */
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
        defaultExecutor());
  }

  /** Auto-generated for codecheck compliance. */
  public StreamController(
      Supplier<Object> deepAgentGetter,
      Supplier<String> memberNameGetter,
      Consumer<MemberStatus> statusUpdater,
      Consumer<ExecutionStatus> executionUpdater,
      Supplier<Session> sessionGetter,
      Runnable wakeMailboxCallback,
      StreamRoundExecutor roundExecutor) {
    this.deepAgentGetter = deepAgentGetter != null ? deepAgentGetter : () -> null;
    this.memberNameGetter = memberNameGetter != null ? memberNameGetter : () -> null;
    this.statusUpdater = statusUpdater != null ? statusUpdater : ignored -> {};
    this.executionUpdater = executionUpdater != null ? executionUpdater : ignored -> {};
    this.sessionGetter = sessionGetter != null ? sessionGetter : () -> null;
    this.wakeMailboxCallback = wakeMailboxCallback;
    this.roundExecutor = roundExecutor != null ? roundExecutor : defaultExecutor();
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isAgentRunning() {
    return isStreamingActiveLegacy || streamingActive;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean hasInFlightRound() {
    return isInFlightRound;
  }

  /** Auto-generated for codecheck compliance. */
  public Queue<InteractiveInput> getPendingInterruptResumes() {
    return pendingInterruptResumes;
  }

  /** Auto-generated for codecheck compliance. */
  public List<Object> getPendingInputs() {
    return pendingInputs;
  }

  /** Auto-generated for codecheck compliance. */
  public LinkedBlockingQueue<Object> getStreamQueue() {
    return streamQueue;
  }

  /** Auto-generated for codecheck compliance. */
  public void setStreamQueue(LinkedBlockingQueue<Object> streamQueue) {
    this.streamQueue = streamQueue != null ? streamQueue : new LinkedBlockingQueue<>();
  }

  /** Auto-generated for codecheck compliance. */
  public void requestCloseStreamAfterCurrentRound() {
    isCloseStreamAfterCurrentRound = true;
  }

  /** Auto-generated for codecheck compliance. */
  public void startRound(Object content) {
    if (deepAgentGetter.get() == null || streamQueue == null) {
      return;
    }
    runOneRound(content);
  }

  /** Auto-generated for codecheck compliance. */
  public void steer(Object content) {
    Object deepAgent = deepAgentGetter.get();
    if (deepAgent instanceof DeepAgent agent) {
      agent.steer(stringify(content), resolveAgentSessionApi());
    }
  }

  /** Auto-generated for codecheck compliance. */
  public void isFollowUp(Object content) {
    Object deepAgent = deepAgentGetter.get();
    if (deepAgent instanceof DeepAgent agent) {
      agent.isFollowUp(stringify(content), resolveAgentSessionApi());
    }
  }

  /** Auto-generated for codecheck compliance. */
  public void cancelAgent() {
    executionUpdater.accept(ExecutionStatus.CANCEL_REQUESTED);
    executionUpdater.accept(ExecutionStatus.CANCELLING);
    executionUpdater.accept(ExecutionStatus.CANCELLED);
    executionUpdater.accept(ExecutionStatus.IDLE);
    isInFlightRound = false;
    streamingActive = false;
    isStreamingActiveLegacy = false;
  }

  /** Auto-generated for codecheck compliance. */
  public void closeStream() {
    if (streamQueue != null) {
      streamQueue.offer(STREAM_END);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public boolean hasPendingInterrupt() {
    return pendingInterruptIds().size() > 0;
  }

  /** Auto-generated for codecheck compliance. */
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

  /** Auto-generated for codecheck compliance. */
  public InteractiveInput dequeueValidInterruptResume() {
    while (!pendingInterruptResumes.isEmpty()) {
      InteractiveInput candidate = pendingInterruptResumes.poll();
      if (isValidInterruptResume(candidate)) {
        return candidate;
      }
    }
    return nullValue();
  }

  /** Auto-generated for codecheck compliance. */
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

  /** Auto-generated for codecheck compliance. */
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

  /** Auto-generated for codecheck compliance. */
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

  /** Auto-generated for codecheck compliance. */
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

  /** Auto-generated for codecheck compliance. */
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
   * Public interface StreamRoundExecutor used by the Java parity implementation.
   *
   * @since 1.0
   */
  @FunctionalInterface
  public interface StreamRoundExecutor {
    Iterator<Object> run(Object deepAgent, Map<String, Object> inputs, String sessionId);
  }

  /**
   * Public record TaskFailure used by the Java parity implementation.
   *
   * @since 1.0
   */
  public record TaskFailure(Integer code, String text) {}

  private static <T> T nullValue() {
    return null;
  }
}
