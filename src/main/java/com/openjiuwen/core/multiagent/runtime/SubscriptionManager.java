/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Topic subscription registry with wildcard matching. */
public class SubscriptionManager {
  private final Map<String, Set<String>> subscriptions = new LinkedHashMap<>();
  private final Map<String, Set<String>> agentTopics = new LinkedHashMap<>();

  /** Auto-generated for codecheck compliance. */
  public synchronized void subscribe(String agentId, String topicPattern) {
    subscriptions.computeIfAbsent(topicPattern, ignored -> new LinkedHashSet<>()).add(agentId);
    agentTopics.computeIfAbsent(agentId, ignored -> new LinkedHashSet<>()).add(topicPattern);
  }

  /** Auto-generated for codecheck compliance. */
  public synchronized void unsubscribe(String agentId, String topicPattern) {
    Set<String> subscribers = subscriptions.get(topicPattern);
    if (subscribers != null) {
      subscribers.remove(agentId);
      if (subscribers.isEmpty()) {
        subscriptions.remove(topicPattern);
      }
    }

    Set<String> topics = agentTopics.get(agentId);
    if (topics != null) {
      topics.remove(topicPattern);
      if (topics.isEmpty()) {
        agentTopics.remove(agentId);
      }
    }
  }

  /** Auto-generated for codecheck compliance. */
  public synchronized void unsubscribeAll(String agentId) {
    Set<String> topics = agentTopics.remove(agentId);
    if (topics == null) {
      return;
    }
    for (String topic : topics) {
      Set<String> subscribers = subscriptions.get(topic);
      if (subscribers == null) {
        continue;
      }
      subscribers.remove(agentId);
      if (subscribers.isEmpty()) {
        subscriptions.remove(topic);
      }
    }
  }

  /** Auto-generated for codecheck compliance. */
  public synchronized List<String> getSubscribers(String topicId) {
    Set<String> isResolved = new LinkedHashSet<>();
    for (Map.Entry<String, Set<String>> entry : subscriptions.entrySet()) {
      if (matches(topicId, entry.getKey())) {
        isResolved.addAll(entry.getValue());
      }
    }
    return new ArrayList<>(isResolved);
  }

  /** Auto-generated for codecheck compliance. */
  public synchronized int getSubscriptionCount() {
    return subscriptions.values().stream().mapToInt(Set::size).sum();
  }

  /** Auto-generated for codecheck compliance. */
  public synchronized Map<String, Object> listSubscriptions(String agentId) {
    if (agentId != null) {
      return Map.of(
          "agent_id",
          agentId,
          "topics",
          new ArrayList<>(agentTopics.getOrDefault(agentId, Set.of())));
    }

    Map<String, Object> snapshot = new LinkedHashMap<>();
    Map<String, List<String>> all = new LinkedHashMap<>();
    for (Map.Entry<String, Set<String>> entry : subscriptions.entrySet()) {
      all.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    snapshot.put("subscriptions", all);
    return snapshot;
  }

  private static boolean matches(String topicId, String pattern) {
    if (topicId.equals(pattern)) {
      return true;
    }
    if (!pattern.contains("*") && !pattern.contains("?")) {
      return false;
    }
    return Pattern.matches(globToRegex(pattern), topicId);
  }

  private static String globToRegex(String pattern) {
    StringBuilder builder = new StringBuilder("^");
    for (int i = 0; i < pattern.length(); i++) {
      char current = pattern.charAt(i);
      switch (current) {
        case '*' -> builder.append(".*");
        case '?' -> builder.append('.');
        case '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\' ->
            builder.append('\\').append(current);
        default -> builder.append(current);
      }
    }
    builder.append('$');
    return builder.toString();
  }
}
