/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.deepagents.middlewares;

import com.openjiuwen.harness.schema.AgentMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Task Planning Middleware for deep agents.
 *
 * <p>Mirrors Python's {@code task_planning_middleware} module in {@code
 * openjiuwen.deepagents.middlewares}.
 *
 * <p>This middleware provides a lightweight, inspection-friendly planning surface similar to
 * Python's task-planning rail output shape.
 */
public class TaskPlanningMiddleware {

  /**
   * Plans tasks for the given input.
   *
   * <p>Supported inputs:
   *
   * <ul>
   *   <li>{@link String}: becomes a single task item
   *   <li>{@code Map<String, Object>}: may include {@code query}, {@code steps}, {@code mode}, and
   *       {@code max_steps}
   * </ul>
   *
   * @param task task input source
   * @return normalized planning payload for task-loop consumption
   */
  public Map<String, Object> plan(Object task) {
    if (task instanceof String query) {
      return buildPlan(query, List.of(query), AgentMode.PLAN.name().toLowerCase(Locale.ROOT), 1);
    }
    if (task instanceof Map<?, ?> map) {
      Object rawQuery = map.containsKey("query") ? map.get("query") : map.get("task");
      String query = stringValue(rawQuery);
      List<String> steps = extractSteps(map.get("steps"));
      int maxSteps =
          map.get("max_steps") instanceof Number number
              ? number.intValue()
              : Math.max(steps.size(), 1);
      String mode =
          stringValue(
              map.containsKey("mode")
                  ? map.get("mode")
                  : AgentMode.PLAN.name().toLowerCase(Locale.ROOT));
      if (steps.isEmpty() && !query.isBlank()) {
        steps = List.of(query);
      }
      return buildPlan(query, steps, mode, maxSteps);
    }
    throw new IllegalArgumentException(
        "Unsupported task input: " + (task == null ? "null" : task.getClass().getName()));
  }

  private Map<String, Object> buildPlan(
      String query, List<String> steps, String mode, int maxSteps) {
    List<Map<String, Object>> items = new ArrayList<>();
    int index = 1;
    for (String step : steps.stream().limit(Math.max(maxSteps, 1)).toList()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", "step-" + index);
      item.put("content", step);
      item.put("status", index == 1 ? "in_progress" : "pending");
      items.add(item);
      index++;
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("query", query);
    result.put(
        "mode",
        mode == null || mode.isBlank() ? AgentMode.PLAN.name().toLowerCase(Locale.ROOT) : mode);
    result.put("count", items.size());
    result.put("items", items);
    result.put("requires_user_input", false);
    return result;
  }

  private List<String> extractSteps(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object item : list) {
      if (item != null && !String.valueOf(item).isBlank()) {
        result.add(String.valueOf(item));
      }
    }
    return result;
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
