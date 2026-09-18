/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

/**
 * User-facing hook that customizes a member runtime's underlying agent.
 *
 * <p>Mirrors Python's {@code AgentCustomizer} in
 * {@code openjiuwen/agent_teams/agent/member_runtime.py}.</p>
 */
@FunctionalInterface
public interface AgentCustomizer {

    void customize(Object agent, String memberName, String roleValue);
}
