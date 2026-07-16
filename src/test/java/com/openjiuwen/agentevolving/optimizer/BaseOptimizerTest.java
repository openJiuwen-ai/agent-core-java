
package com.openjiuwen.agentevolving.optimizer;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.Updates;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.session.Session;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class BaseOptimizerTest {
    @Test
    void filterOperatorsMatchesMapBasedTunables() {
        Map<String, Object> operators = Map.of("op1",
                new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system"))),
                "op2", new FakeOperator("op2", Map.of("other", new TunableSpec("other", "prompt", "other"))));

        Map<String, Object> result = BaseOptimizer.filterOperators(operators, List.of("system_prompt"));

        assertEquals(1, result.size());
        assertTrue(result.containsKey("op1"));
    }

    @Test
    void bindInitializesParametersForMatchingOperators() {
        TestOptimizer optimizer = new TestOptimizer();
        int count = optimizer.bind(
                Map.of("op1",
                        new FakeOperator("op1",
                                Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of("system_prompt"), Map.of());

        assertEquals(1, count);
        assertTrue(optimizer.parameters().containsKey("op1"));
    }

    @Test
    void addTrajectoryCachesCopyAccessibleList() {
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.bind(
                Map.of("op1",
                        new FakeOperator("op1",
                                Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of("system_prompt"), Map.of());

        Trajectory trajectory = Trajectory.builder().caseId("c1").executionId("e1").steps(List.of()).build();
        optimizer.addTrajectory(trajectory);

        List<Trajectory> trajectories = optimizer.getTrajectories();
        assertEquals(1, trajectories.size());
        assertNotSame(trajectories, optimizer.getTrajectories());
    }

    @Test
    void backwardAndStepDelegateToSubclassHooks() {
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.bind(
                Map.of("op1",
                        new FakeOperator("op1",
                                Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of("system_prompt"), Map.of());

        EvaluatedCase evaluatedCase = EvaluatedCase.builder().score(0.0).build();
        optimizer.backward(List.of(evaluatedCase));
        Updates updates = optimizer.step();

        assertEquals(1, optimizer.backwardCallCount);
        assertEquals(1, optimizer.stepCallCount);
        assertEquals("value", updates.get("op1", "system_prompt"));
        assertEquals(1, optimizer.getBadCases().size());
    }

    @Test
    void stepWithoutBoundParametersRaisesToolchainError() {
        TestOptimizer optimizer = new TestOptimizer();

        assertThrows(BaseError.class, optimizer::step);
    }

    private static final class TestOptimizer extends BaseOptimizer {
        private int backwardCallCount;
        private int stepCallCount;

        @Override
        protected Updates doStep() {
            stepCallCount++;
            return Updates.of("op1", "system_prompt", "value");
        }

        @Override
        protected void doBackward(List<EvaluatedCase> evaluatedCases) {
            backwardCallCount++;
        }
    }

    private static final class FakeOperator extends Operator {
        private final String operatorId;
        private final Map<String, TunableSpec> tunables;

        private FakeOperator(String operatorId, Map<String, TunableSpec> tunables) {
            this.operatorId = operatorId;
            this.tunables = new LinkedHashMap<>(tunables);
        }

        @Override
        public String getOperatorId() {
            return operatorId;
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return tunables;
        }

        @Override
        public void setParameter(String target, Object value) {
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of("state", "value");
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) {
            return Map.of();
        }

        @Override
        public OperatorStream<?> stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) {
            return null;
        }
    }
}
