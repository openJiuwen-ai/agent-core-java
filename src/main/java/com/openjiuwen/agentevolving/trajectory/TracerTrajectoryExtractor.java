/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import com.openjiuwen.agentevolving.trajectory.extractor.TrajectoryExtractor;

/**
 * Backward-compatible alias for {@link TrajectoryExtractor}.
 *
 * <p>Mirrors Python's {@code TracerTrajectoryExtractor} alias in
 * {@code openjiuwen/agent_evolving/trajectory/operation.py}.</p>
 */
public class TracerTrajectoryExtractor extends TrajectoryExtractor {

    public TracerTrajectoryExtractor() {
        super();
    }

    public TracerTrajectoryExtractor(Object resourceManager) {
        super(resourceManager);
    }
}
