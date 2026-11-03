/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import com.openjiuwen.agentevolving.trajectory.extractor.TrajectoryExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TrajectoryOperationTest {

    @Test
    void operationBridgeMirrorsPythonAliasModule() {
        assertEquals("openjiuwen/agent_evolving/trajectory/operation.py", TrajectoryOperation.PYTHON_MODULE);
        assertEquals(TrajectoryExtractor.class, TrajectoryOperation.TRAJECTORY_EXTRACTOR);
        assertEquals(TracerTrajectoryExtractor.class, TrajectoryOperation.TRACER_TRAJECTORY_EXTRACTOR);
    }

    @Test
    void tracerAliasExtendsCanonicalExtractor() {
        assertInstanceOf(TrajectoryExtractor.class, new TracerTrajectoryExtractor());
        assertInstanceOf(TrajectoryExtractor.class, new TracerTrajectoryExtractor(new Object()));
    }
}
