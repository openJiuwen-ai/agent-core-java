/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline.store;

import com.openjiuwen.agentevolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests.system_tests.agent_evolving.agent_rl.offline.store.test_null_store_e2e}
 * in {@code tests/system_tests/agent_evolving/agent_rl/offline/store/test_null_store_e2e.py}.
 */
class NullRolloutStoreE2eMissingTest {

    @Test
    void testNullStoreE2eSaveRolloutAndSummaryNoError() {
        NullRolloutStore store = new NullRolloutStore();

        store.saveRollout(0, "t1", message(), "train");
        store.saveRollout(0, "t1", message(), "val");
        store.saveStepSummary(0, Map.of("loss", 0.1));
    }

    @Test
    void testNullStoreE2eQueryReturnsEmpty() {
        NullRolloutStore store = new NullRolloutStore();

        assertThat(store.queryRollouts(Map.of(), 100)).isEmpty();
    }

    @Test
    void testNullStoreE2eCloseNoError() {
        NullRolloutStore store = new NullRolloutStore();

        store.close();
    }

    private static RolloutMessage message() {
        Rollout rollout = new Rollout();
        rollout.setTurnId(0);
        rollout.setInputPrompt(Map.of());
        rollout.setOutputResponse(Map.of());

        RolloutMessage message = new RolloutMessage();
        message.setTaskId("t1");
        message.setOriginTaskId("o1");
        message.setRolloutId("r1");
        message.setRolloutInfo(List.of(rollout));
        message.setRewardList(List.of(0.5));
        message.setGlobalReward(0.5);
        message.setTurnCount(1);
        return message;
    }
}
