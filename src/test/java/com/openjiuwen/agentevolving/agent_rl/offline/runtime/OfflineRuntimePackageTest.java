/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.RLRail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl.offline.runtime} module in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/runtime/__init__.py}.
 */
class OfflineRuntimePackageTest {

    @Test
    void exportedNamesMirrorPythonAll() {
        assertEquals(List.of("RLRail"), OfflineRuntimePackage.EXPORTED_NAMES);
        assertEquals(List.of(RLRail.class), OfflineRuntimePackage.EXPORTED_TYPES);
    }

    @Test
    void descriptionPreservesRuntimePackageDocstringIntent() {
        assertTrue(OfflineRuntimePackage.DESCRIPTION.contains("offline RL rollout generation"));
        assertTrue(OfflineRuntimePackage.DESCRIPTION.contains("trajectory collection"));
        assertTrue(OfflineRuntimePackage.DESCRIPTION.contains("RLRail"));
    }
}
