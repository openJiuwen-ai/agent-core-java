/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

import com.openjiuwen.core.session.AgentSessionApi;

/**
 * Execution-layer availability strategy for named tools.
 *
 * <p>Implementations decide whether a tool call is allowed before the tool is resolved and
 * executed. The default allows all tools to preserve behavior for agents without registered groups.
 *
 * @since 0.1.17
 */
public interface ToolAvailabilityChecker {

    boolean isAvailable(String toolName, AgentSessionApi session);
}
