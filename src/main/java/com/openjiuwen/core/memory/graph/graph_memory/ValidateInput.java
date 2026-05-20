/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.memory.config.graph.EpisodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Input validation for graph memory add and search operations. */
public final class ValidateInput {
  private static final String STORE_TYPE = "graph mem store";

  private ValidateInput() {}

  /** Auto-generated for codecheck compliance. */
  public static void validateAddMemoryInput(
      int userIdMaxLength,
      EpisodeType srcType,
      String userId,
      Map<String, String> contentFmtKwargs) {
    if (contentFmtKwargs != null && contentFmtKwargs.isEmpty()) {
      throw new IllegalArgumentException(
          "When supplied, content_fmt_kwargs must be of type dict[str, str] and not empty");
    }
    if (contentFmtKwargs != null) {
      for (Map.Entry<String, String> entry : contentFmtKwargs.entrySet()) {
        if (entry.getKey() == null
            || entry.getKey().isBlank()
            || entry.getValue() == null
            || entry.getValue().isBlank()) {
          throw new IllegalArgumentException(
              "content_fmt_kwargs must have non-empty keys and values of string type");
        }
      }
    }
    if (srcType == null) {
      throw new IllegalArgumentException(
          "src_type must be one of [EpisodeType.CONVERSATION, EpisodeType.DOCUMENT,"
              + " EpisodeType.JSON]");
    }
    if (userId == null || userId.trim().isEmpty() || userId.trim().length() > userIdMaxLength) {
      throw new IllegalArgumentException(
          "user_id must be a string of length <= " + userIdMaxLength + " (preferably UUID4)");
    }
  }

  /** Auto-generated for codecheck compliance. */
  @SuppressWarnings("unchecked")
  /** Auto-generated for codecheck compliance. */
  public static List<String> validateSearchInput(
      String query, Object userId, List<Boolean> settings) {
    if (query == null || query.trim().isEmpty()) {
      throw new IllegalArgumentException("query must be a non-empty string value");
    }
    List<String> users;
    if (userId instanceof List<?> list) {
      users = new ArrayList<>();
      for (Object value : list) {
        users.add(String.valueOf(value));
      }
    } else {
      users = List.of(String.valueOf(userId));
    }
    for (String user : users) {
      if (user == null || user.trim().isEmpty() || user.length() > 32) {
        throw new IllegalArgumentException(
            "user_id must be a non-empty string of length <= 32 or a list of such strings");
      }
    }
    for (Boolean isSetting : settings) {
      if (isSetting == null) {
        throw new IllegalArgumentException(
            "entity, relation, episode must be boolean values True or False");
      }
    }
    return users;
  }
}
