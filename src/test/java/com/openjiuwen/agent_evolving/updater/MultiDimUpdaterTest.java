/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.agent_evolving.updater;

import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiDimUpdaterTest {

    @Test
    void requiresForwardDataChecksOptimizerInstances() {
        TestMultiDimUpdater updater = new TestMultiDimUpdater(Map.of(
                "llm", new ForwardAwareOptimizer(true),
                "tool", new ForwardAwareOptimizer(false)
        ));

        assertTrue(updater.requiresForwardData());
    }

    @Test
    void requiresForwardDataReturnsFalseWhenNoOptimizerNeedsIt() {
        TestMultiDimUpdater updater = new TestMultiDimUpdater(Map.of(
                "llm", new ForwardAwareOptimizer(false),
                "tool", new ForwardAwareOptimizer(false)
        ));

        assertFalse(updater.requiresForwardData());
    }

    private static final class ForwardAwareOptimizer {
        private final boolean requiresForwardData;

        private ForwardAwareOptimizer(boolean requiresForwardData) {
            this.requiresForwardData = requiresForwardData;
        }

        public boolean requiresForwardData() {
            return requiresForwardData;
        }
    }

    private static final class TestMultiDimUpdater extends MultiDimUpdater {

        private TestMultiDimUpdater(Map<String, Object> domainOptimizers) {
            super(domainOptimizers);
        }

        @Override
        public int bind(Map<String, Object> operators, List<String> targets, Map<String, Object> config) {
            return 0;
        }

        @Override
        public Object update(List<Trajectory> trajectories, List<Object> evaluatedCases, Map<String, Object> config) {
            return null;
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of();
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }
    }
}
