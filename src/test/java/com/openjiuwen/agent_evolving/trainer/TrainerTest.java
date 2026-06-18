/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trainer;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.CaseLoader;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.evaluator.BaseEvaluator;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.agent_evolving.updater.Updater;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.BaseAgent;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's trainer public behavior in
 * {@code openjiuwen/agent_evolving/trainer/trainer.py}.
 */
class TrainerTest {

    @Test
    void earlyStopSkipsUpdaterUpdate() {
        RecordingUpdater updater = new RecordingUpdater();
        FixedEvaluator evaluator = new FixedEvaluator(0.99d);
        Trainer trainer = Trainer.builder()
                .updater(updater)
                .evaluator(evaluator)
                .earlyStopScore(0.95d)
                .build();
        TestAgent agent = new TestAgent(Map.of("op1", new RecordingOperator("op1")));

        BaseAgent result = trainer.train(agent, cases(2), null, 10, Map.of());

        assertSame(agent, result);
        assertEquals(1, updater.bindCount);
        assertEquals(0, updater.updateCount);
        assertTrue(evaluator.batchCount > 0);
    }

    @Test
    void noOperatorMatchSoftExits() {
        RecordingUpdater updater = new RecordingUpdater();
        updater.bindReturn = 0;
        Trainer trainer = Trainer.builder()
                .updater(updater)
                .evaluator(new FixedEvaluator(0.8d))
                .build();
        TestAgent agent = new TestAgent(Map.of("op1", new RecordingOperator("op1")));

        BaseAgent result = trainer.train(agent, cases(1), null, 1, Map.of());

        assertSame(agent, result);
        assertEquals(1, updater.bindCount);
        assertEquals(0, updater.updateCount);
    }

    @Test
    void applyUpdatesSetsExistingOperatorParametersOnly() {
        RecordingOperator operator = new RecordingOperator("op1");
        Updates updates = new Updates();
        updates.put("op1", "system_prompt", "new prompt");
        updates.put("missing", "system_prompt", "ignored");
        updates.put("op1", "null_value", null);

        Trainer.applyUpdates(Map.of("op1", operator), updates);

        assertEquals(Map.of("system_prompt", "new prompt"), operator.parameters);
    }

    @Test
    void predictAndEvaluateReturnExpectedShapes() {
        Trainer trainer = Trainer.builder()
                .updater(new RecordingUpdater())
                .evaluator(new FixedEvaluator(0.75d))
                .build();
        TestAgent agent = new TestAgent(Map.of("op1", new RecordingOperator("op1")));
        CaseLoader loader = cases(3);

        Trainer.PredictionResult prediction = trainer.predict(agent, loader);
        Trainer.EvaluationResult evaluation = trainer.evaluate(agent, loader);

        assertEquals(3, prediction.predictions().size());
        assertEquals(3, prediction.sessions().size());
        assertEquals(0.75d, evaluation.score());
        assertEquals(3, evaluation.evaluatedCases().size());
    }

    private static CaseLoader cases(int count) {
        List<Case> values = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add(new Case(
                    Map.of("query", "question " + index),
                    Map.of("answer", "expected"),
                    null,
                    "case_" + index
            ));
        }
        return new CaseLoader(values);
    }

    private static final class RecordingUpdater implements Updater {
        private int bindReturn = 1;
        private int bindCount;
        private int updateCount;
        private boolean requiresForward = true;

        @Override
        public int bind(Map<String, Operator> operators, List<String> targets, Map<String, Object> config) {
            bindCount++;
            return bindReturn;
        }

        @Override
        public boolean requiresForwardData() {
            return requiresForward;
        }

        @Override
        public CompletionStage<Object> update(
                List<Trajectory> trajectories,
                List<Object> evaluatedCases,
                Map<String, Object> config) {
            updateCount++;
            return CompletableFuture.completedFuture(new Updates());
        }

        @Override
        public CompletionStage<Object> process(
                List<Trajectory> trajectories,
                List<EvolutionSignal> signals,
                Map<String, Object> config) {
            return CompletableFuture.completedFuture(new Updates());
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of();
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }
    }

    private static final class FixedEvaluator extends BaseEvaluator {
        private final double score;
        private int batchCount;

        private FixedEvaluator(double score) {
            this.score = score;
        }

        @Override
        public EvaluatedCase evaluate(Case caseValue, Map<String, Object> predict) {
            return new EvaluatedCase(caseValue, predict, score, "", null);
        }

        @Override
        public List<EvaluatedCase> batchEvaluate(List<Case> cases, List<Map<String, Object>> predicts, int numParallel) {
            batchCount++;
            return super.batchEvaluate(cases, predicts, numParallel);
        }
    }

    private static final class RecordingOperator extends Operator {
        private final String operatorId;
        private final Map<String, Object> parameters = new LinkedHashMap<>();

        private RecordingOperator(String operatorId) {
            this.operatorId = operatorId;
        }

        @Override
        public String getOperatorId() {
            return operatorId;
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return Map.of();
        }

        @Override
        public Map<String, Object> getState() {
            return new HashMap<>(parameters);
        }

        @Override
        public void setParameter(String target, Object value) {
            parameters.put(target, value);
        }

        @Override
        public void loadState(Map<String, Object> state) {
            parameters.clear();
            parameters.putAll(state);
        }
    }

    private static final class TestAgent extends BaseAgent {
        private final Map<String, Operator> operators;

        private TestAgent(Map<String, Operator> operators) {
            super(new AgentCard("agent", "agent", "agent"));
            this.operators = operators;
        }

        public Map<String, Operator> getOperators() {
            return operators;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            if (inputs instanceof Map<?, ?> map) {
                return CompletableFuture.completedFuture(Map.of("output", map.get("query")));
            }
            return CompletableFuture.completedFuture(Map.of("output", inputs));
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }
}
