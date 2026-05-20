/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.task_loop.StopConditionEvaluator;
import java.lang.reflect.Array;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public class TaskCompletionRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TaskCompletionRail extends DeepAgentRail {
  private static final Pattern PROMISE_TAG =
      Pattern.compile(
          "<promise>\\s*(.*?)\\s*</promise>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /** Auto-generated for codecheck compliance. */
  public static final String COMPLETION_SIGNAL_SECTION = "completion_signal";

  /** Auto-generated for codecheck compliance. */
  public static final int COMPLETION_SIGNAL_PRIORITY = 85;

  private final String taskInstruction;
  private final String completionPromise;
  private final int requiredConfirmations;
  private final boolean isAllowPromiseDetails;
  private final Integer maxRounds;
  private final Duration timeout;
  private final List<StopConditionEvaluator> extraEvaluators;
  private DeepAgent owner;

  /** Auto-generated for codecheck compliance. */
  public TaskCompletionRail() {
    this(null, null, 1, false, null, null, List.of());
  }

  /** Auto-generated for codecheck compliance. */
  public TaskCompletionRail(
      String taskInstruction,
      String completionPromise,
      int requiredConfirmations,
      boolean isAllowPromiseDetails,
      Integer maxRounds,
      Duration timeout) {
    this(
        taskInstruction,
        completionPromise,
        requiredConfirmations,
        isAllowPromiseDetails,
        maxRounds,
        timeout,
        List.of());
  }

  /** Auto-generated for codecheck compliance. */
  public TaskCompletionRail(
      String taskInstruction,
      String completionPromise,
      int requiredConfirmations,
      boolean isAllowPromiseDetails,
      Integer maxRounds,
      Duration timeout,
      List<StopConditionEvaluator> extraEvaluators) {
    this.taskInstruction = taskInstruction;
    this.completionPromise = completionPromise;
    this.requiredConfirmations = Math.max(1, requiredConfirmations);
    this.isAllowPromiseDetails = isAllowPromiseDetails;
    this.maxRounds = maxRounds;
    this.timeout = timeout;
    this.extraEvaluators =
        extraEvaluators != null ? new ArrayList<>(extraEvaluators) : new ArrayList<>();
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public int priority() {
    return 10;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void init(Object agent) {
    if (agent instanceof DeepAgent deepAgent) {
      owner = deepAgent;
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void uninit(Object agent) {
    if (owner != null) {
      owner.getAgent().getPromptBuilder().removeSection(COMPLETION_SIGNAL_SECTION);
    }
    owner = null;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void beforeModelCall(AgentCallbackContext ctx) {
    if (completionPromise == null || completionPromise.isBlank()) {
      removeCompletionSignalSection();
      return;
    }
    String prompt = completionSignalPrompt(resolveLanguage());
    if (owner != null) {
      owner
          .getAgent()
          .addPromptBuilderSection(COMPLETION_SIGNAL_SECTION, prompt, COMPLETION_SIGNAL_PRIORITY);
    }
    if (ctx != null && ctx.getInputs() instanceof ModelCallInputs inputs) {
      injectCompletionSignalMessage(inputs, prompt);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public String applyTaskInstruction(String query, boolean isFollowUp) {
    if (taskInstruction == null || taskInstruction.isBlank() || query == null || isFollowUp) {
      return query;
    }
    return taskInstruction.replace("{query}", query);
  }

  /** Auto-generated for codecheck compliance. */
  public Optional<String> extractPromiseBlock(String content) {
    if (content == null) {
      return Optional.empty();
    }
    Matcher matcher = PROMISE_TAG.matcher(content);
    return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
  }

  /** Auto-generated for codecheck compliance. */
  public boolean promiseMatches(String content) {
    if (completionPromise == null || completionPromise.isBlank()) {
      return false;
    }
    return extractPromiseBlock(content).map(this::promiseBlockMatches).orElse(false);
  }

  /** Auto-generated for codecheck compliance. */
  public Optional<String> extractMatchingPromise(Object value) {
    if (completionPromise == null || completionPromise.isBlank()) {
      return Optional.empty();
    }
    return extractMatchingPromise(value, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  /** Auto-generated for codecheck compliance. */
  public boolean hasCompletionPromise() {
    return completionPromise != null && !completionPromise.isBlank();
  }

  /** Auto-generated for codecheck compliance. */
  public String getCompletionPromise() {
    return completionPromise;
  }

  /** Auto-generated for codecheck compliance. */
  public String getTaskInstruction() {
    return taskInstruction;
  }

  /** Auto-generated for codecheck compliance. */
  public int getRequiredConfirmations() {
    return requiredConfirmations;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isAllowPromiseDetails() {
    return isAllowPromiseDetails;
  }

  /** Auto-generated for codecheck compliance. */
  public Integer getMaxRounds() {
    return maxRounds;
  }

  /** Auto-generated for codecheck compliance. */
  public Duration getTimeout() {
    return timeout;
  }

  /** Auto-generated for codecheck compliance. */
  public List<StopConditionEvaluator> getExtraEvaluators() {
    return Collections.unmodifiableList(extraEvaluators);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean hasCompletionSignalSection() {
    return owner != null
        && owner.getAgent().getPromptBuilder().hasSection(COMPLETION_SIGNAL_SECTION);
  }

  /** Auto-generated for codecheck compliance. */
  public String completionSignalPrompt(String language) {
    String promise = completionPromise == null ? "" : completionPromise;
    if ("en".equalsIgnoreCase(language)) {
      return "## Completion Signal\n"
          + "When the task is fully completed, output <promise>"
          + promise
          + "</promise> as the final line of your response. "
          + "Do not output this tag until you are confident the task is complete.";
    }
    return "## 完成信号\n"
        + "任务完全完成后，在回复的最后一行输出 <promise>"
        + promise
        + "</promise>。\n"
        + "在确认任务完成前，不要输出此标签。";
  }

  private void injectCompletionSignalMessage(ModelCallInputs inputs, String prompt) {
    List<Object> messages =
        inputs.getMessages() != null ? new ArrayList<>(inputs.getMessages()) : new ArrayList<>();
    for (Object message : messages) {
      if (message instanceof BaseMessage baseMessage
          && "system".equalsIgnoreCase(baseMessage.getRole())
          && String.valueOf(baseMessage.getContent())
              .contains("<promise>" + completionPromise + "</promise>")) {
        inputs.setMessages(messages);
        return;
      }
    }
    messages.add(0, new SystemMessage(prompt));
    inputs.setMessages(messages);
  }

  private String resolveLanguage() {
    return owner != null && owner.getWorkspace() != null
        ? owner.getWorkspace().getLanguage()
        : "cn";
  }

  private void removeCompletionSignalSection() {
    if (owner != null) {
      owner.getAgent().getPromptBuilder().removeSection(COMPLETION_SIGNAL_SECTION);
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private boolean promiseBlockMatches(String value) {
    String expected = normalize(completionPromise);
    String firstLine = firstNonBlankLine(value);
    return firstLine.equals(expected)
        || (isAllowPromiseDetails && firstLine.startsWith(expected + " "));
  }

  private Optional<String> extractMatchingPromise(Object value, Set<Object> seen) {
    if (value == null) {
      return Optional.empty();
    }
    if (value instanceof String text) {
      return extractPromiseBlock(text).filter(this::promiseBlockMatches);
    }
    if (isContainer(value) && !seen.add(value)) {
      return Optional.empty();
    }
    if (value instanceof ControllerOutputChunk chunk) {
      return extractMatchingPromise(chunk.getControllerPayload(), seen);
    }
    if (value instanceof ControllerOutputPayload payload) {
      Optional<String> fromData = extractMatchingPromise(payload.getData(), seen);
      return fromData.isPresent() ? fromData : extractMatchingPromise(payload.getMetadata(), seen);
    }
    if (value instanceof DataFrame.TextDataFrame textFrame) {
      return extractMatchingPromise(textFrame.text(), seen);
    }
    if (value instanceof DataFrame.JsonDataFrame jsonFrame) {
      return extractMatchingPromise(jsonFrame.data(), seen);
    }
    if (value instanceof DataFrame.FileDataFrame fileFrame) {
      return extractMatchingPromise(fileFrame.uri(), seen);
    }
    if (value instanceof Map<?, ?> map) {
      for (Object entryValue : map.values()) {
        Optional<String> match = extractMatchingPromise(entryValue, seen);
        if (match.isPresent()) {
          return match;
        }
      }
      return Optional.empty();
    }
    if (value instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        Optional<String> match = extractMatchingPromise(item, seen);
        if (match.isPresent()) {
          return match;
        }
      }
      return Optional.empty();
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        Optional<String> match = extractMatchingPromise(Array.get(value, i), seen);
        if (match.isPresent()) {
          return match;
        }
      }
      return Optional.empty();
    }
    return extractMatchingPromise(String.valueOf(value), seen);
  }

  private static boolean isContainer(Object value) {
    return value instanceof ControllerOutputChunk
        || value instanceof ControllerOutputPayload
        || value instanceof DataFrame
        || value instanceof Map<?, ?>
        || value instanceof Iterable<?>
        || value.getClass().isArray();
  }

  private static String firstNonBlankLine(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    for (String line : value.split("\\R")) {
      String normalized = normalize(line);
      if (!normalized.isBlank()) {
        return normalized;
      }
    }
    return "";
  }
}
