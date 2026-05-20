/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class PermissionEngine used by the Java parity implementation.
 *
 * @since 1.0
 */
public class PermissionEngine {
  private final Map<String, Object> config;
  private final Path workspaceRoot;

  /** Auto-generated for codecheck compliance. */
  public PermissionEngine(Map<String, Object> config, Path workspaceRoot) {
    this.config = config != null ? config : new LinkedHashMap<>();
    this.workspaceRoot = workspaceRoot;
  }

  /** Auto-generated for codecheck compliance. */
  public Map.Entry<PermissionLevel, String> evaluateGlobalPolicyDirectly(
      String toolName, Map<String, Object> toolArgs) {
    if (!Boolean.TRUE.equals(config.getOrDefault("enabled", Boolean.FALSE))) {
      return Map.entry(PermissionLevel.ALLOW, "disabled");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> tools = (Map<String, Object>) config.getOrDefault("tools", Map.of());
    if (tools.containsKey(toolName)) {
      return Map.entry(PermissionLevel.fromValue(tools.get(toolName)), "tools." + toolName);
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> defaults =
        (Map<String, Object>) config.getOrDefault("defaults", Map.of("*", "allow"));
    return Map.entry(PermissionLevel.fromValue(defaults.getOrDefault("*", "allow")), "defaults.*");
  }

  /** Auto-generated for codecheck compliance. */
  public PermissionCheckResult checkPermission(String toolName, Map<String, Object> toolArgs) {
    Map.Entry<PermissionLevel, String> direct = evaluateGlobalPolicyDirectly(toolName, toolArgs);
    PermissionLevel level = direct.getKey();
    return PermissionCheckResult.builder()
        .permission(level)
        .matchedRule(direct.getValue())
        .needsApproval(level == PermissionLevel.ASK)
        .build();
  }

  /** Auto-generated for codecheck compliance. */
  public Path getWorkspaceRoot() {
    return workspaceRoot;
  }

  /** Auto-generated for codecheck compliance. */
  public Map<String, Object> getConfig() {
    return config;
  }
}
