/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import com.openjiuwen.agent_evolving.agent_rl.rl_trainer.VerlExecutor;
import com.openjiuwen.agent_evolving.trajectory.StepKind;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RlRailAndVerlExecutorTest {

    @Test
    void rlRailAddsRlMetadataToLlmSteps() {
        RlRail rail = new RlRail("session-1", "rl_test", "case-1");
        TrajectoryStep step = TrajectoryStep.builder()
                .kind(StepKind.LLM)
                .meta(Map.of("existing", "value"))
                .build();

        rail.processStep(step);

        assertEquals(1, rail.getLlmStepCount());
        assertEquals(0, step.getMeta().get("turn_id"));
        assertEquals("rl_test", step.getMeta().get("source"));
        assertEquals("case-1", step.getMeta().get("case_id"));
        assertEquals("session-1", step.getMeta().get("session_id"));
        assertEquals("value", step.getMeta().get("existing"));
    }

    @Test
    void verlExecutorTracksLifecycleConfig() {
        Object config = Map.of("trainer", "ppo");

        VerlExecutor.initialize(config);
        assertTrue(VerlExecutor.isInitialized());
        assertSame(config, VerlExecutor.getCurrentConfig());

        VerlExecutor.shutdown();
        assertFalse(VerlExecutor.isInitialized());
        assertEquals(null, VerlExecutor.getCurrentConfig());
    }
}
