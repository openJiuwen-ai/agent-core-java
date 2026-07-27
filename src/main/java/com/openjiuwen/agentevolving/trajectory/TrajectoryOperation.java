/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import com.openjiuwen.agentevolving.trajectory.extractor.TrajectoryExtractor;

/**
 * Compatibility bridge for trajectory operation exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.trajectory.operation} in
 * {@code openjiuwen/agent_evolving/trajectory/operation.py}.</p>
 */
public final class TrajectoryOperation {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/trajectory/operation.py";
    public static final Class<TrajectoryExtractor> TRAJECTORY_EXTRACTOR = TrajectoryExtractor.class;
    public static final Class<TracerTrajectoryExtractor> TRACER_TRAJECTORY_EXTRACTOR =
            TracerTrajectoryExtractor.class;

    private TrajectoryOperation() {
    }
}
