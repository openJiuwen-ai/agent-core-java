/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.store;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code NullRolloutStore} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/store/null_store.py}.
 *
 * <p>Mirrors Python's null-store unit tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/store/test_null_store.py}.</p>
 */
class NullRolloutStoreTest {

    @Test
    void testSaveRolloutSaveStepSummaryAndCloseNoError() {
        NullRolloutStore store = new NullRolloutStore();
        RolloutMessage message = new RolloutMessage();
        message.setTaskId("t1");
        message.setOriginTaskId("o1");
        message.setRolloutId("r1");

        store.saveRollout(0, "t1", message, "train");
        store.saveRollout(0, "t1", message, "val");
        store.saveStepSummary(0, Map.of("loss", 0.1));
        store.close();
    }

    @Test
    void testQueryRolloutsReturnsEmptyList() {
        NullRolloutStore store = new NullRolloutStore();
        assertEquals(List.of(), store.queryRollouts(Map.of(), 10));
    }
}
