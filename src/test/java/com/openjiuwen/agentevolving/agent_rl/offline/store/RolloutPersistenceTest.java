/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.store;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RolloutPersistenceTest {

    @Test
    void defaultMethodsPreservePythonDefaults() {
        FakePersistence persistence = new FakePersistence();

        persistence.saveRollout(3, "task-1", null);
        persistence.queryRollouts(Map.of("phase", "train"));

        assertEquals("train", persistence.lastPhase);
        assertEquals(100, persistence.lastLimit);
    }

    private static final class FakePersistence implements RolloutPersistence {
        private String lastPhase;
        private int lastLimit;

        @Override
        public void saveRollout(int step, String taskId, RolloutMessage rollout, String phase) {
            this.lastPhase = phase;
        }

        @Override
        public void saveStepSummary(int step, Map<String, Object> metrics) {
        }

        @Override
        public List<Map<String, Object>> queryRollouts(Map<String, Object> filters, int limit) {
            this.lastLimit = limit;
            return List.of();
        }

        @Override
        public void close() {
        }
    }
}
