/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import com.openjiuwen.agentevolving.trajectory.LLMCallDetail;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Python-aligned helpers for agent_rl schemas. */
public final class AgentRlSchemas {
  private AgentRlSchemas() {}

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Convert a trajectory into RL rollouts, mirroring Python's trajectory_to_rollouts().
   *
   * @param trajectory source trajectory
   * @return rollout list converted from trajectory steps
   */
  public static List<Rollout> trajectoryToRollouts(Trajectory trajectory) {
    List<Rollout> rollouts = new ArrayList<>();
    if (trajectory == null || trajectory.getSteps() == null) {
      return rollouts;
    }
    for (TrajectoryStep step : trajectory.getSteps()) {
      if (step == null || step.getKindEnum() == null || !"llm".equals(step.getKind())) {
        continue;
      }
      Object detailObj = step.getDetail();
      if (detailObj == null) {
        continue;
      }

      List<Object> rawMessages =
          detailObj instanceof LLMCallDetail llmCallDetail
              ? llmCallDetail.getMessages()
              : readListProperty(detailObj, "messages");
      List<Object> messagesNorm = new ArrayList<>();
      if (rawMessages != null) {
        for (Object message : rawMessages) {
          messagesNorm.add(normalizeMessageLike(message));
        }
      }

      List<Object> rawTools =
          detailObj instanceof LLMCallDetail llmCallDetail
              ? llmCallDetail.getTools()
              : readListProperty(detailObj, "tools");
      List<Object> toolsNorm = null;
      if (rawTools != null) {
        toolsNorm = new ArrayList<>();
        for (Object tool : rawTools) {
          toolsNorm.add(normalizePojo(tool));
        }
      }

      Object rawResponse =
          detailObj instanceof LLMCallDetail llmCallDetail
              ? llmCallDetail.getResponse()
              : readProperty(detailObj, "response");
      Map<String, Object> outputResponse = normalizeResponse(rawResponse);

      Map<String, Object> inputPrompt = new LinkedHashMap<>();
      inputPrompt.put("message", messagesNorm);
      inputPrompt.put("tools", toolsNorm);

      Map<String, Object> llmConfig = null;
      if (step.getMeta() != null) {
        Object llmConfigObj = step.getMeta().get("llm_config");
        if (llmConfigObj instanceof Map<?, ?> map) {
          llmConfig = castStringObjectMap(map);
        }
      }

      rollouts.add(
          new Rollout(
              rollouts.size(),
              inputPrompt,
              outputResponse,
              llmConfig,
              normalizeTokenIds(step.getPromptTokenIds()),
              normalizeTokenIds(step.getCompletionTokenIds())));
    }
    return rollouts;
  }

  private static List<Object> readListProperty(Object target, String name) {
    Object value = readProperty(target, name);
    if (value instanceof List<?> list) {
      return new ArrayList<>(list);
    }
    return List.of();
  }

  private static Object readProperty(Object target, String name) {
    try {
      return target
          .getClass()
          .getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1))
          .invoke(target);
    } catch (ReflectiveOperationException | SecurityException ignored) {
      return null;
    }
  }

  private static Map<String, Object> normalizeResponse(Object rawResponse) {
    if (rawResponse == null) {
      return null;
    }
    if (rawResponse instanceof Map<?, ?> map) {
      return castStringObjectMap(map);
    }
    if (rawResponse instanceof String stringResponse) {
      Map<String, Object> wrapped = new LinkedHashMap<>();
      wrapped.put("role", "assistant");
      wrapped.put("content", stringResponse);
      return wrapped;
    }
    if (rawResponse instanceof AssistantMessage assistantMessage) {
      return assistantMessage.toApiFormat();
    }
    Map<String, Object> dumped = normalizePojo(rawResponse);
    if (dumped != null) {
      return dumped;
    }
    Map<String, Object> fallback = new LinkedHashMap<>();
    fallback.put("role", readStringLike(rawResponse, "role", "assistant"));
    fallback.put("content", readStringLike(rawResponse, "content", ""));
    return fallback;
  }

  private static Object normalizeMessageLike(Object raw) {
    if (raw instanceof Map<?, ?> map) {
      return castStringObjectMap(map);
    }
    if (raw instanceof AssistantMessage assistantMessage) {
      return assistantMessage.toApiFormat();
    }
    if (raw instanceof BaseMessage baseMessage) {
      Map<String, Object> message = new LinkedHashMap<>();
      message.put("role", baseMessage.getRole());
      message.put("content", baseMessage.getContent());
      if (baseMessage.getName() != null) {
        message.put("name", baseMessage.getName());
      }
      if (baseMessage.getMetadata() != null) {
        message.put("metadata", baseMessage.getMetadata());
      }
      return message;
    }
    Map<String, Object> dumped = normalizePojo(raw);
    if (dumped != null) {
      return dumped;
    }
    return raw;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> normalizePojo(Object raw) {
    if (raw == null) {
      return null;
    }
    try {
      return MAPPER.convertValue(raw, LinkedHashMap.class);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static String readStringLike(Object raw, String property, String defaultValue) {
    Object value = readProperty(raw, property);
    if (value instanceof String stringValue) {
      return stringValue;
    }
    return defaultValue;
  }

  private static List<Integer> normalizeTokenIds(List<Integer> tokenIds) {
    return tokenIds == null || tokenIds.isEmpty() ? null : tokenIds;
  }

  private static Map<String, Object> castStringObjectMap(Map<?, ?> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      result.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return result;
  }

}
