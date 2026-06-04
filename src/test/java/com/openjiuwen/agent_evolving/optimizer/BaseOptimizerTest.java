package com.openjiuwen.agent_evolving.optimizer;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.session.Session;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BaseOptimizer and TextualParameter.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.test_base_optimizer}.</p>
 */
class BaseOptimizerTest {

    @Test
    void filterOperatorsMatchesMapBasedTunables() {
        Map<String, Object> operators = Map.of(
                "op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system"))),
                "op2", new FakeOperator("op2", Map.of("other", new TunableSpec("other", "prompt", "other")))
        );

        Map<String, Object> result = BaseOptimizer.filterOperators(operators, List.of("system_prompt"));

        assertEquals(1, result.size());
        assertTrue(result.containsKey("op1"));
    }

    @Test
    void bindInitializesParametersForMatchingOperators() {
        TestOptimizer optimizer = new TestOptimizer();
        int count = optimizer.bind(
                Map.of("op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of("system_prompt"),
                Map.of()
        );

        assertEquals(1, count);
        assertTrue(optimizer.parameters().containsKey("op1"));
    }

    @Test
    void bindUsesDefaultTargetsWhenTargetsEmpty() {
        TestOptimizer optimizer = new TestOptimizer();
        int count = optimizer.bind(
                Map.of("op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of(),
                Map.of()
        );

        assertEquals(1, count);
        assertTrue(optimizer.parameters().containsKey("op1"));
    }

    @Test
    void bindWithNullOperatorsReturnsZero() {
        TestOptimizer optimizer = new TestOptimizer();

        int count = optimizer.bind(null, null, Map.of());

        assertEquals(0, count);
    }

    @Test
    void filterOperatorsSkipsEmptyTargets() {
        Map<String, Object> operators = Map.of("op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system"))));

        Map<String, Object> result = BaseOptimizer.filterOperators(operators, List.of());

        assertEquals(Map.of(), result);
    }

    @Test
    void filterOperatorsMatchesAnyTarget() {
        Map<String, Object> operators = Map.of(
                "op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system"))),
                "op2", new FakeOperator("op2", Map.of("user_prompt", new TunableSpec("user_prompt", "prompt", "user")))
        );

        Map<String, Object> result = BaseOptimizer.filterOperators(operators, List.of("system_prompt", "user_prompt"));

        assertEquals(2, result.size());
        assertTrue(result.containsKey("op1"));
        assertTrue(result.containsKey("op2"));
    }

    @Test
    void filterOperatorsSkipsOperatorsWithoutTunables() {
        Map<String, Object> operators = Map.of("op1", new FakeOperator("op1", Map.of()));

        Map<String, Object> result = BaseOptimizer.filterOperators(operators, List.of("system_prompt"));

        assertEquals(Map.of(), result);
    }

    @Test
    void requiresForwardDataDefaultsTrue() {
        TestOptimizer optimizer = new TestOptimizer();

        assertTrue(optimizer.requiresForwardData());
    }

    @Test
    void addTrajectoryCachesCopyAccessibleList() {
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.bind(
                Map.of("op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of("system_prompt"),
                Map.of()
        );

        Trajectory trajectory = Trajectory.builder().caseId("c1").executionId("e1").steps(List.of()).build();
        optimizer.addTrajectory(trajectory);

        List<Trajectory> trajectories = optimizer.getTrajectories();
        assertEquals(1, trajectories.size());
        assertNotSame(trajectories, optimizer.getTrajectories());
    }

    @Test
    void getTrajectoriesReturnsCopy() {
        TestOptimizer optimizer = new TestOptimizer();
        Trajectory traj1 = Trajectory.builder().caseId("c1").executionId("e1").steps(List.of()).build();
        Trajectory traj2 = Trajectory.builder().caseId("c2").executionId("e2").steps(List.of()).build();
        optimizer.addTrajectory(traj1);
        optimizer.addTrajectory(traj2);

        List<Trajectory> result = optimizer.getTrajectories();

        assertEquals(2, result.size());
        assertNotSame(result, optimizer.getTrajectories());
    }

    @Test
    void clearTrajectoriesEmptiesCache() {
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.addTrajectory(Trajectory.builder().caseId("c1").executionId("e1").steps(List.of()).build());
        optimizer.addTrajectory(Trajectory.builder().caseId("c2").executionId("e2").steps(List.of()).build());

        optimizer.clearTrajectories();

        assertEquals(List.of(), optimizer.getTrajectories());
    }

    @Test
    void backwardAndStepDelegateToSubclassHooks() {
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.bind(
                Map.of("op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of("system_prompt"),
                Map.of()
        );

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

    @Test
    void parametersReturnsCopyOfBoundParameters() {
        TestOptimizer optimizer = new TestOptimizer();
        optimizer.bind(
                Map.of("op1", new FakeOperator("op1", Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "system")))),
                List.of("system_prompt"),
                Map.of()
        );

        Map<String, TextualParameter> result = optimizer.parameters();

        assertTrue(result.containsKey("op1"));
        assertNotSame(result, optimizer.parameters());
    }

    @Test
    void textualParameterInitializesWithEmptyState() {
        TextualParameter param = new TextualParameter("test_op");

        assertEquals("test_op", param.getOperatorId());
        assertNull(param.getGradient("anything"));
        assertEquals("", param.getDescription());
    }

    @Test
    void textualParameterStoresAndReturnsGradient() {
        TextualParameter param = new TextualParameter("op1");

        param.setGradient("system_prompt", "improved prompt");

        assertEquals("improved prompt", param.getGradient("system_prompt"));
    }

    @Test
    void textualParameterReturnsNullForMissingGradient() {
        TextualParameter param = new TextualParameter("op1");

        assertNull(param.getGradient("missing"));
    }

    @Test
    void textualParameterStoresDescription() {
        TextualParameter param = new TextualParameter("op1");

        param.setDescription("Test optimizer param");

        assertEquals("Test optimizer param", param.getDescription());
    }

    @Test
    void textualParameterSupportsMultipleGradients() {
        TextualParameter param = new TextualParameter("op1");
        param.setGradient("system_prompt", "sys grad");
        param.setGradient("user_prompt", "usr grad");

        assertEquals("sys grad", param.getGradient("system_prompt"));
        assertEquals("usr grad", param.getGradient("user_prompt"));
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

        @Override
        public List<String> defaultTargets() {
            return List.of("system_prompt");
        }
    }

    private static final class FakeOperator extends Operator {

        private final String operatorId;
        private final Map<String, TunableSpec> tunables;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private FakeOperator(String operatorId, Map<String, TunableSpec> tunables) {
            this.operatorId = operatorId;
            this.tunables = new LinkedHashMap<>(tunables);
            this.state.put("state", "value");
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
            state.put(target, value);
        }

        @Override
        public Map<String, Object> getState() {
            return new LinkedHashMap<>(state);
        }

        @Override
        public void loadState(Map<String, Object> state) {
            this.state.clear();
            if (state != null) {
                this.state.putAll(state);
            }
        }

        public Object invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) {
            return Map.of();
        }

        public OperatorStream<?> stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) {
            return null;
        }
    }
}
