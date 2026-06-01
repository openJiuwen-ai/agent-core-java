/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.store;

import com.openjiuwen.agent_evolving.agent_rl.offline.store.NullRolloutStore;
import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for NullRolloutStore.
 * <p>
 * Mirrors Python's {@code test_null_store_e2e.py} in
 * {@code tests/system_tests/agent_evolving/agent_rl/offline/store/}.
 */
@DisplayName("NullStore Tests")
class TestNullStore {

    @Test
    @DisplayName("save rollout and summary have no side effects or errors")
    void testNullStoreE2eSaveRolloutAndSummaryNoError() {
        NullRolloutStore store = new NullRolloutStore();
        Rollout rollout = new Rollout();
        rollout.setTurnId(0);
        rollout.setInputPrompt(Map.of());
        rollout.setOutputResponse(Map.of());

        RolloutMessage msg = new RolloutMessage();
        msg.setTaskId("t1");
        msg.setOriginTaskId("o1");
        msg.setRolloutId("r1");
        msg.setRolloutInfo(List.of(rollout));
        msg.setRewardList(List.of(0.5));
        msg.setGlobalReward(0.5);
        msg.setTurnCount(1);

        store.saveRollout(0, "t1", msg, "train");
        store.saveRollout(0, "t1", msg, "val");
        store.saveStepSummary(0, Map.of("loss", 0.1));
    }

    @Test
    @DisplayName("query returns empty")
    void testNullStoreE2eQueryReturnsEmpty() {
        NullRolloutStore store = new NullRolloutStore();

        assertThat(store.queryRollouts(Map.of(), 100)).isEmpty();
    }

    @Test
    @DisplayName("close is no-op")
    void testNullStoreE2eCloseNoError() {
        NullRolloutStore store = new NullRolloutStore();
        store.close();
    }
}
