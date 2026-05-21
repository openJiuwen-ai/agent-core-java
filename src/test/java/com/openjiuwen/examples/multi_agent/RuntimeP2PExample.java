/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.multi_agent;

/**
 * TeamRuntime P2P Communication Example - Documentation placeholder.
 * <p>
 * Demonstrates Point-to-Point (P2P) communication pattern:
 * Planner → Coder → Reviewer sequential collaboration.
 * <p>
 * Mirrors Python's {@code runtime_p2p} in
 * {@code examples.multi_agent.runtime_p2p}.
 * <p>
 * Pattern:
 * <pre>
 * PlannerAgent (规划) → CoderAgent (编码) → ReviewerAgent (审查)
 * </pre>
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link com.openjiuwen.core.multiagent.BaseGroup} - Group management</li>
 *   <li>{@link com.openjiuwen.core.multiagent.Session} - Multi-agent session</li>
 * </ul>
 */
public final class RuntimeP2PExample {

    private RuntimeP2PExample() {}

    // Agent identifiers
    public static final String PLANNER_AGENT = "planner";
    public static final String CODER_AGENT = "coder";
    public static final String REVIEWER_AGENT = "reviewer";

    // Communication pattern: P2P sequential
    public static final String PATTERN = "P2P_SEQUENTIAL";
}