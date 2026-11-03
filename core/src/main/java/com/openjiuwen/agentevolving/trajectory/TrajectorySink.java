/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

/**
 * Mirrors Python's {@code TrajectorySink} in
 * {@code openjiuwen/agent_evolving/trajectory/registry.py}.
 */
public interface TrajectorySink {

    void publishMemberTrajectory(MemberTrajectorySnapshot snapshot);
}
