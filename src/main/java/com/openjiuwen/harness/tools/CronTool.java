/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Public class CronTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class CronTool {
  private final CronToolBackend backend;
  private final CronToolContext context;

  /** Auto-generated for codecheck compliance. */
  public CronTool(CronToolBackend backend, CronToolContext context) {
    this.backend = backend;
    this.context = context;
  }

  /** Auto-generated for codecheck compliance. */
  public ToolOutput invoke(String action, Map<String, Object> payload) {
    if (action == null || action.isBlank()) {
      return ToolOutput.builder().success(false).error("action is required").build();
    }
    Map<String, Object> params = payload != null ? payload : Map.of();
    try {
      String normalized = action.trim().toLowerCase(Locale.ROOT);
      Object result =
          switch (normalized) {
            case "status" -> backend.status();
            case "list" ->
                Map.of(
                    "jobs", backend.listJobs(Boolean.TRUE.equals(params.get("includeDisabled"))));
            case "add" -> backend.createJob(new LinkedHashMap<>(params), context);
            case "update" ->
                backend.updateJob(
                    String.valueOf(params.get("jobId")),
                    new LinkedHashMap<>(castMap(params.get("patch"))),
                    context);
            case "remove" ->
                Map.of("deleted", backend.deleteJob(String.valueOf(params.get("jobId"))));
            case "run" -> Map.of("run_id", backend.runNow(String.valueOf(params.get("jobId"))));
            case "runs" -> Map.of("runs", backend.getRuns(String.valueOf(params.get("jobId")), 20));
            case "wake" ->
                backend.wake(
                    String.valueOf(params.getOrDefault("text", "")),
                    context,
                    params.get("mode") instanceof String mode ? mode : null);
            default -> throw new IllegalArgumentException("unsupported cron action");
          };
      return ToolOutput.builder().success(true).data(result).build();
    } catch (Exception ex) {
      return ToolOutput.builder().success(false).error(ex.getMessage()).build();
    }
  }

  private static Map<String, Object> castMap(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Object> normalized = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      normalized.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return normalized;
  }
}
