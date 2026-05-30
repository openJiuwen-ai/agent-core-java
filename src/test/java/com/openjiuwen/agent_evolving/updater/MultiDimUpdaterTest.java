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

    @Test
    void requiresForwardDataUsesPythonStyleMethodName() {
        TestMultiDimUpdater updater = new TestMultiDimUpdater(Map.of(
                "llm", new PythonStyleForwardAwareOptimizer(true)
        ));

        assertTrue(updater.requiresForwardData());
    }

    @Test
    void requiresForwardDataUsesPythonTruthinessForReturnValue() {
        TestMultiDimUpdater truthyUpdater = new TestMultiDimUpdater(Map.of(
                "llm", new TruthyForwardAwareOptimizer("yes")
        ));
        TestMultiDimUpdater falseyUpdater = new TestMultiDimUpdater(Map.of(
                "llm", new TruthyForwardAwareOptimizer("")
        ));

        assertTrue(truthyUpdater.requiresForwardData());
        assertFalse(falseyUpdater.requiresForwardData());
    }

    @Test
    void requiresForwardDataIgnoresOptimizersWithoutHook() {
        TestMultiDimUpdater updater = new TestMultiDimUpdater(Map.of(
                "memory", new Object()
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

    private static final class PythonStyleForwardAwareOptimizer {
        private final boolean requiresForwardData;

        private PythonStyleForwardAwareOptimizer(boolean requiresForwardData) {
            this.requiresForwardData = requiresForwardData;
        }

        public boolean requires_forward_data() {
            return requiresForwardData;
        }
    }

    private static final class TruthyForwardAwareOptimizer {
        private final Object result;

        private TruthyForwardAwareOptimizer(Object result) {
            this.result = result;
        }

        public Object requires_forward_data() {
            return result;
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
