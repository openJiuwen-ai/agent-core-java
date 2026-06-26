/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import com.openjiuwen.agent_evolving.agent_rl.config.RLConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.rail.RLOnlineRail;
import com.openjiuwen.agent_evolving.agent_rl.optimizer.OfflineRLOptimizer;
import com.openjiuwen.agent_evolving.agent_rl.optimizer.OnlineRLOptimizer;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl} module in
 * {@code openjiuwen/agent_evolving/agent_rl/__init__.py}.</p>
 */
class AgentRlPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals("openjiuwen/agent_evolving/agent_rl/__init__.py", AgentRlPackage.PYTHON_MODULE);
        assertEquals(
                List.of(
                        "RLConfig",
                        "OfflineRLOptimizer",
                        "OnlineRLOptimizer",
                        "RewardRegistry",
                        "RLRail",
                        "RLOnlineRail",
                        "RLTask",
                        "Rollout",
                        "RolloutMessage",
                        "RolloutWithReward"
                ),
                AgentRlPackage.all()
        );
    }

    @Test
    void typeLookupMirrorsModuleAttributeExports() {
        assertSame(RLConfig.class, AgentRlPackage.getAttribute("RLConfig"));
        assertSame(OfflineRLOptimizer.class, AgentRlPackage.getAttribute("OfflineRLOptimizer"));
        assertSame(OnlineRLOptimizer.class, AgentRlPackage.getAttribute("OnlineRLOptimizer"));
        assertSame(RewardRegistry.class, AgentRlPackage.getAttribute("RewardRegistry"));
        assertSame(RLRail.class, AgentRlPackage.getAttribute("RLRail"));
        assertSame(RLOnlineRail.class, AgentRlPackage.getAttribute("RLOnlineRail"));
        assertSame(RLTask.class, AgentRlPackage.getAttribute("RLTask"));
        assertSame(Rollout.class, AgentRlPackage.getAttribute("Rollout"));
        assertSame(RolloutMessage.class, AgentRlPackage.getAttribute("RolloutMessage"));
        assertSame(RolloutWithReward.class, AgentRlPackage.getAttribute("RolloutWithReward"));
    }

    @Test
    void unknownAttributeUsesPythonModuleErrorShape() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentRlPackage.getAttribute("missing")
        );
        assertEquals(
                "module 'openjiuwen.agent_evolving.agent_rl' has no attribute 'missing'",
                exception.getMessage()
        );
    }

    @Test
    void lazyLoggerPatchIsAlreadyRepresentedByJavaLazyLogger() {
        assertTrue(AgentRlPackage.isLazyLoggerPatchApplied());
    }
}
