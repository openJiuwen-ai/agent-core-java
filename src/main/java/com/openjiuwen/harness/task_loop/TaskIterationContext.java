/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
/**
 * Public class TaskIterationContext used by the Java parity implementation.
 *
 * @since 1.0
 */
@Builder
public class TaskIterationContext {
  private DeepAgent agent;
  private Task task;
  private AgentSessionApi session;
  private int round;
  private boolean isFollowUp;
  @Builder.Default private Map<String, Object> inputs = new LinkedHashMap<>();
  @Builder.Default private Map<String, Object> result = new LinkedHashMap<>();
  private UsageMetadata usageMetadata;
  private Exception exception;

  /** Auto-generated for codecheck compliance. */
  public static class TaskIterationContextBuilder {
    /** Auto-generated for codecheck compliance. */
    public TaskIterationContextBuilder followUp(boolean value) {
      return this.isFollowUp(value);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public String taskId() {
    return task != null ? task.getTaskId() : stringValue(inputs.get("task_id"));
  }

  /** Auto-generated for codecheck compliance. */
  public String sessionId() {
    if (session != null && session.getSessionId() != null) {
      return session.getSessionId();
    }
    return task != null ? task.getSessionId() : stringValue(inputs.get("conversation_id"));
  }

  /** Auto-generated for codecheck compliance. */
  public UsageMetadata resolvedUsageMetadata() {
    if (usageMetadata != null) {
      return usageMetadata;
    }
    UsageMetadata fromResult = usageMetadataFrom(result);
    return fromResult != null ? fromResult : usageMetadataFrom(inputs);
  }

  /** Auto-generated for codecheck compliance. */
  public int tokenUsage() {
    UsageMetadata usage = resolvedUsageMetadata();
    if (usage != null) {
      return usage.getTotalTokens();
    }
    int fromResult =
        intValue(firstNonNull(result, new String[] {"token_usage", "total_tokens"}), -1);
    return fromResult >= 0
        ? fromResult
        : intValue(firstNonNull(inputs, new String[] {"token_usage", "total_tokens"}), 0);
  }

  /** Auto-generated for codecheck compliance. */
  public static UsageMetadata usageMetadataFrom(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      return null;
    }
    Object direct = firstNonNull(payload, new String[] {"usage_metadata", "usage"});
    if (direct instanceof UsageMetadata usageMetadata) {
      return usageMetadata;
    }
    if (direct instanceof Map<?, ?> usageMap) {
      return usageMetadataFromMap(usageMap);
    }
    if (direct instanceof Number number) {
      return UsageMetadata.builder().totalTokens(number.intValue()).build();
    }
    Object tokenUsage = firstNonNull(payload, new String[] {"token_usage", "total_tokens"});
    if (tokenUsage != null) {
      return UsageMetadata.builder().totalTokens(intValue(tokenUsage, 0)).build();
    }
    return null;
  }

  private static UsageMetadata usageMetadataFromMap(Map<?, ?> usageMap) {
    int inputTokens =
        intValue(
            firstNonNull(
                usageMap,
                new String[] {"input_tokens", "inputTokens", "prompt_tokens", "promptTokens"}),
            0);
    int outputTokens =
        intValue(
            firstNonNull(
                usageMap,
                new String[] {
                  "output_tokens", "outputTokens", "completion_tokens", "completionTokens"
                }),
            0);
    int totalTokens =
        intValue(
            firstNonNull(
                usageMap,
                new String[] {"total_tokens", "totalTokens", "token_usage", "tokenUsage"}),
            inputTokens + outputTokens);
    return UsageMetadata.builder()
        .modelName(
            stringValue(firstNonNull(usageMap, new String[] {"model_name", "modelName", "model"})))
        .inputTokens(inputTokens)
        .outputTokens(outputTokens)
        .totalTokens(totalTokens)
        .cacheTokens(
            intValue(
                firstNonNull(
                    usageMap, new String[] {"cache_tokens", "cacheTokens", "cached_tokens"}),
                0))
        .build();
  }

  private static Object firstNonNull(Map<?, ?> source, String[] keys) {
    for (String key : keys) {
      Object value = source.get(key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static int intValue(Object value, int fallback) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value != null && !String.valueOf(value).isBlank()) {
      try {
        return Integer.parseInt(String.valueOf(value));
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }
}
