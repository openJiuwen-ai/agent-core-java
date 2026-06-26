/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

/**
 * Mirrors Python's {@code TrajectorySource} in
 * {@code openjiuwen/agent_evolving/trajectory/registry.py}.
 */
public interface TrajectorySource {

    Trajectory getTrajectory(String teamId, String sessionId, boolean filterCollaborative);
}
