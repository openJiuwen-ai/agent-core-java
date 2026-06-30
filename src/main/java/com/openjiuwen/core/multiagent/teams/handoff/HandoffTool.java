/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import java.util.Map;

/**
 * Public class HandoffTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class HandoffTool {
  private final String targetId;

  /** Auto-generated for codecheck compliance. */
  public HandoffTool(String targetId) {
    this.targetId = targetId;
  }

  /** Auto-generated for codecheck compliance. */
  public Map<String, Object> invoke(Map<String, Object> inputs) {
    return Map.of(
        HandoffSignal.HANDOFF_TARGET_KEY, targetId,
        HandoffSignal.HANDOFF_MESSAGE_KEY,
            inputs != null ? String.valueOf(inputs.getOrDefault("message", "")) : "",
        HandoffSignal.HANDOFF_REASON_KEY,
            inputs != null ? String.valueOf(inputs.getOrDefault("reason", "")) : "");
  }
}
