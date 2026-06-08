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
 * Mirrors the focused no-op store checks from Python's null-store tests.
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
