/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

/**
 * Immutable route rule: source agent may hand off to target agent.
 * 
 * @since 0.1.7
 */
public record HandoffRoute(String source, String target) {
}
