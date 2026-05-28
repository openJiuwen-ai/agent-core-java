/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.multi_agent;

/**
 * BaseTeam Hybrid Communication Example - Documentation placeholder.
 * <p>
 * Encapsulates hybrid communication (P2P + Pub-Sub) into TaskExecutionTeam.
 * Exposes unified invoke() interface.
 * <p>
 * Mirrors Python's {@code team_hybrid} in
 * {@code examples.multi_agent.team_hybrid}.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link com.openjiuwen.core.multiagent.BaseGroup} - Team management</li>
 *   <li>{@link com.openjiuwen.core.multiagent.GroupConfig} - Team configuration</li>
 * </ul>
 */
public final class TeamHybridExample {

    private TeamHybridExample() {}

    public static final String TEAM_ID = "task_execution_team";
}