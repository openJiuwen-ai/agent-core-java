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
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Missing-test parity for trainer orchestration, progress, callbacks, and data edge cases.
 *
 * <p>Mirrors Python's tests in
 * {@code tests/unit_tests/agent_evolving/trainer/test_trainer.py}.</p>
 */
class TrainerMissingTest {

    @Test
    void initWithRequiredArgsUsesUpdaterAndEvaluator() {
        RecordingUpdater updater = createMockUpdater();
        RecordingEvaluator evaluator = createMockEvaluator(0.8d);
        Trainer trainer = trainer(updater, evaluator).build();

        BaseAgent agent = createMockAgent();
        BaseAgent result = trainer.train(agent, createCaseLoader(), null, 1, Map.of());

        assertSame(agent, result);
        assertEquals(1, updater.bindCount);
        assertTrue(evaluator.batchCount > 0);
    }

    @Test
    void initWithOptionalConfigAffectsBehavior(@TempDir Path tempDir) {
        RecordingUpdater updater = createMockUpdater();
        RecordingEvaluator evaluator = createMockEvaluator(0.99d);
        Trainer trainer = trainer(updater, evaluator)
                .numParallel(4)
                .earlyStopScore(0.95d)
                .checkpointDir(tempDir.toString())
                .build();

        BaseAgent agent = createMockAgent();
        BaseAgent result = trainer.train(agent, createCaseLoader(), null, 10, Map.of());

        assertSame(agent, result);
        assertEquals(0, updater.updateCount);
        assertEquals(4, trainer.getNumParallel());
        assertNotNull(trainer.getCheckpointStore());
    }

    @Test
    void initDefaultsProduceDefaultBehavior() {
        RecordingUpdater updater = createMockUpdater();
        Trainer trainer = trainer(updater, createMockEvaluator(0.8d)).build();

        trainer.train(createMockAgent(), createCaseLoader(), null, 1, Map.of());

        assertEquals(1, updater.bindCount);
        assertEquals(1, updater.updateCount);
        assertEquals(1, trainer.getNumParallel());
    }

    @Test
    void emptyUpdatesDoNothing() {
        RecordingOperator operator = new RecordingOperator("op1");

        Trainer.applyUpdates(Map.of("op1", operator), new Updates());

        assertEquals(Map.of(), operator.parameters);
    }

    @Test
    void singleUpdateCallsSetParameter() {
        RecordingOperator operator = new RecordingOperator("op1");

        Trainer.applyUpdates(Map.of("op1", operator), Updates.of("op1", "system_prompt", "new prompt"));

        assertEquals(Map.of("system_prompt", "new prompt"), operator.parameters);
    }

    @Test
    void multipleUpdatesCallEachOperator() {
        RecordingOperator first = new RecordingOperator("op1");
        RecordingOperator second = new RecordingOperator("op2");
        Updates updates = new Updates();
        updates.put("op1", "p1", "v1");
        updates.put("op2", "p2", "v2");

        Trainer.applyUpdates(Map.of("op1", first, "op2", second), updates);

        assertEquals(Map.of("p1", "v1"), first.parameters);
        assertEquals(Map.of("p2", "v2"), second.parameters);
    }

    @Test
    void skipsMissingOperatorUpdates() {
        RecordingOperator operator = new RecordingOperator("op1");

        Trainer.applyUpdates(Map.of("op1", operator), Updates.of("missing_op", "param", "value"));

        assertEquals(Map.of(), operator.parameters);
    }

    @Test
    void skipsNullValueUpdates() {
        RecordingOperator operator = new RecordingOperator("op1");

        Trainer.applyUpdates(Map.of("op1", operator), Updates.of("op1", "param", null));

        assertEquals(Map.of(), operator.parameters);
    }

    @Test
    void evaluateCallsBatchEvaluate() {
        RecordingEvaluator evaluator = createMockEvaluator(0.8d);
        Trainer trainer = trainer(createMockUpdater(), evaluator).build();

        Trainer.EvaluationResult result = trainer.evaluate(createMockAgent(), createCaseLoader());

        assertEquals(1, evaluator.batchCount);
        assertEquals(0.8d, result.score(), 1.0e-12d);
        assertEquals(3, result.evaluatedCases().size());
    }

    @Test
    void predictReturnsPredictionsAndSessions() {
        Trainer trainer = trainer(createMockUpdater(), createMockEvaluator(0.8d)).build();

        Trainer.PredictionResult result = trainer.predict(createMockAgent(), createCaseLoader());

        assertEquals(3, result.predictions().size());
        assertEquals(3, result.sessions().size());
    }

    @Test
    void predictOnlyReturnsOnlyPredictions() {
        Trainer trainer = trainer(createMockUpdater(), createMockEvaluator(0.8d)).build();

        List<Map<String, Object>> result = trainer.predictOnly(createMockAgent(), createCaseLoader());

        assertEquals(3, result.size());
    }

    @Test
    void forwardReturnsTupleShape() {
        Trainer trainer = trainer(createMockUpdater(), createMockEvaluator(0.8d)).build();

        Trainer.ForwardResult result = trainer.forward(createMockAgent(), createCaseLoader());

        assertEquals(0.8d, result.score(), 1.0e-12d);
        assertEquals(3, result.evaluatedCases().size());
        assertEquals(3, result.trajectories().size());
        assertEquals(3, result.sessions().size());
    }

    @Test
    void trainReturnsAgentWhenEarlyStopReached() {
        RecordingUpdater updater = createMockUpdater();
        RecordingEvaluator evaluator = createMockEvaluator(0.99d);
        Trainer trainer = trainer(updater, evaluator).earlyStopScore(0.95d).build();
        BaseAgent agent = createMockAgent();

        BaseAgent result = trainer.train(agent, createCaseLoader(), null, 10, Map.of());

        assertSame(agent, result);
        assertEquals(0, updater.updateCount);
    }

    @Test
    void trainReturnsAgentWhenNoOperatorMatchesUpdater() {
        RecordingUpdater updater = createMockUpdater();
        updater.bindReturn = 0;
        Trainer trainer = trainer(updater, createMockEvaluator(0.8d)).build();
        BaseAgent agent = createMockAgent();

        BaseAgent result = trainer.train(agent, createCaseLoader(), null, 3, Map.of());

        assertSame(agent, result);
        assertEquals(1, updater.bindCount);
        assertEquals(0, updater.updateCount);
    }

    @Test
    void callbacksAreInvokedDuringTraining() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        Trainer trainer = trainer(createMockUpdater(), createMockEvaluator(0.5d))
                .numParallel(1)
                .callbacks(callbacks)
                .build();

        trainer.train(createMockAgent(), createCaseLoader(), createCaseLoader(), 2, Map.of());

        assertEquals(1, callbacks.trainBeginCount);
        assertEquals(1, callbacks.trainEndCount);
    }

    @Test
    void predictHandlesInvokeException() {
        Trainer trainer = trainer(createMockUpdater(), createMockEvaluator(0.8d)).build();
        TestAgent agent = createMockAgent();
        agent.failInvoke = true;

        Trainer.PredictionResult result = trainer.predict(agent, createCaseLoader());

        assertEquals(3, result.predictions().size());
        assertTrue(result.predictions().stream().allMatch(item -> item.toString().contains("error")));
    }

    @Test
    void progressDefaults() {
        Progress progress = new Progress();

        assertEquals(0, progress.getStartEpoch());
        assertEquals(0, progress.getCurrentEpoch());
        assertEquals(3, progress.getMaxEpoch());
        assertEquals(0.0d, progress.getBestScore());
        assertEquals(0.0d, progress.getCurrentEpochScore());
    }

    @Test
    void runEpochCountForOne() {
        Progress progress = new Progress();
        progress.setMaxEpoch(1);

        assertEquals(List.of(1), toList(progress.runEpoch()));
    }

    @Test
    void runEpochCountForFive() {
        Progress progress = new Progress();
        progress.setMaxEpoch(5);

        assertEquals(List.of(1, 2, 3, 4, 5), toList(progress.runEpoch()));
    }

    @Test
    void runEpochCountForThree() {
        Progress progress = new Progress();
        progress.setMaxEpoch(3);

        assertEquals(List.of(1, 2, 3), toList(progress.runEpoch()));
    }

    @Test
    void runEpochWithStartEpoch() {
        Progress progress = new Progress();
        progress.setStartEpoch(3);
        progress.setMaxEpoch(5);

        assertEquals(List.of(4, 5), toList(progress.runEpoch()));
    }

    @Test
    void runEpochUpdatesCurrent() {
        Progress progress = new Progress();
        progress.setMaxEpoch(3);

        toList(progress.runEpoch());

        assertEquals(3, progress.getCurrentEpoch());
    }

    @Test
    void partialIterationUpdatesCurrentEpoch() {
        Progress progress = new Progress();
        progress.setMaxEpoch(5);
        Iterator<Integer> iterator = progress.runEpoch().iterator();

        assertEquals(1, iterator.next());
        assertEquals(1, progress.getCurrentEpoch());
        assertEquals(2, iterator.next());
        assertEquals(2, progress.getCurrentEpoch());
    }

    @Test
    void runBatchIteratesConfiguredCount() {
        Progress progress = new Progress();
        progress.setMaxBatchIter(3);

        assertEquals(List.of(0, 1, 2), toList(progress.runBatch()));
        assertEquals(0.0d, progress.getBestBatchScore());
    }

    @Test
    void runBatchResetsBestBatchScore() {
        Progress progress = new Progress();
        progress.setBestBatchScore(0.99d);

        toList(progress.runBatch());

        assertEquals(0.0d, progress.getBestBatchScore());
    }

    @Test
    void progressAcceptsZeroScoreBoundary() {
        Progress progress = new Progress();

        progress.setBestScore(0.0d);
        progress.setCurrentEpochScore(0.0d);

        assertTrue(progress.getBestScore() >= 0.0d);
        assertTrue(progress.getBestScore() <= 1.0d);
    }

    @Test
    void progressAcceptsOneScoreBoundary() {
        Progress progress = new Progress();

        progress.setBestScore(1.0d);
        progress.setCurrentEpochScore(1.0d);

        assertTrue(progress.getBestScore() >= 0.0d);
        assertTrue(progress.getBestScore() <= 1.0d);
    }

    @Test
    void callbacksDefaultImplementationsDoNothing() {
        Callbacks callbacks = new Callbacks();

        assertDoesNotThrow(() -> {
            callbacks.onTrainBegin(createMockAgent(), new Progress(), List.of(createEvaluatedCase()));
            callbacks.onTrainEnd(createMockAgent(), new Progress(), List.of(createEvaluatedCase()));
            callbacks.onTrainEpochBegin(createMockAgent(), new Progress());
            callbacks.onTrainEpochEnd(createMockAgent(), new Progress(), List.of(createEvaluatedCase()));
        });
    }

    @Test
    void customCallbackCanOverrideDefaultBehavior() {
        RecordingCallbacks callbacks = new RecordingCallbacks();

        callbacks.onTrainBegin(createMockAgent(), new Progress(), List.of());
        callbacks.onTrainEnd(createMockAgent(), new Progress(), List.of());

        assertEquals(1, callbacks.trainBeginCount);
        assertEquals(1, callbacks.trainEndCount);
    }

    @Test
    void caseLoaderWithEmptyCases() {
        CaseLoader loader = new CaseLoader(List.of());

        assertEquals(0, loader.getCases().size());
    }

    @Test
    void caseLoaderWithSingleCase() {
        CaseLoader loader = new CaseLoader(List.of(new Case(Map.of("q", "test"), Map.of("ans", "expected"), null, "single")));

        assertEquals(1, loader.getCases().size());
        assertEquals("single", loader.getCases().get(0).getCaseId());
    }

    @Test
    void splitEmptyLoaderReturnsTwoEmptyLoaders() {
        CaseLoader.CaseLoaderSplit split = new CaseLoader(List.of()).split(0.8d);

        assertEquals(0, split.left().getCases().size());
        assertEquals(0, split.right().getCases().size());
    }

    @Test
    void evaluatedCaseClampsNegativeScore() {
        assertEquals(0.0d, createEvaluatedCase(-0.5d).getScore());
    }

    @Test
    void evaluatedCaseClampsAboveOneScore() {
        assertEquals(1.0d, createEvaluatedCase(1.5d).getScore());
    }

    @Test
    void evaluatedCaseKeepsZeroScore() {
        assertEquals(0.0d, createEvaluatedCase(0.0d).getScore());
    }

    @Test
    void evaluatedCaseKeepsOneScore() {
        assertEquals(1.0d, createEvaluatedCase(1.0d).getScore());
    }

    @Test
    void evaluatedCaseDefaultScoreAndReason() {
        EvaluatedCase evaluated = new EvaluatedCase(new Case(Map.of("q", "test"), Map.of("ans", "expected")));

        assertEquals(0.0d, evaluated.getScore());
        assertEquals("", evaluated.getReason());
    }

    @Test
    void evaluatedCaseConvenienceProperties() {
        Case caseValue = new Case(Map.of("q", "test"), Map.of("ans", "expected"), null, "case_id");
        EvaluatedCase evaluated = new EvaluatedCase(caseValue, Map.of("output", "pred"), 0.8d, "Good answer", null);

        assertEquals(caseValue.getInputs(), evaluated.getInputs());
        assertEquals(caseValue.getLabel(), evaluated.getLabel());
        assertEquals(caseValue.getCaseId(), evaluated.getCaseId());
    }

    @Test
    void trainRunsThroughMultipleIterations() {
        RecordingUpdater updater = createMockUpdater();
        Trainer trainer = trainer(updater, createMockEvaluator(0.5d))
                .earlyStopScore(0.95d)
                .numParallel(1)
                .build();

        BaseAgent agent = createMockAgent();
        BaseAgent result = trainer.train(agent, createCaseLoader(), createCaseLoader(), 3, Map.of());

        assertSame(agent, result);
        assertEquals(3, updater.updateCount);
    }

    @Test
    void trainStopsEarlyOnScore() {
        RecordingUpdater updater = createMockUpdater();
        Trainer trainer = trainer(updater, createMockEvaluator(0.95d))
                .earlyStopScore(0.95d)
                .numParallel(1)
                .build();
        BaseAgent agent = createMockAgent();

        BaseAgent result = trainer.train(agent, createCaseLoader(1), null, 10, Map.of());

        assertSame(agent, result);
        assertEquals(0, updater.updateCount);
    }

    @Test
    void blackBoxOptimizerSkipsForward() {
        RecordingUpdater updater = createMockUpdater();
        updater.requiresForward = false;
        updater.updateReturn = Updates.of("op1", "param", "v1");
        RecordingOperator operator = new RecordingOperator("op1");
        TestAgent agent = createMockAgent(Map.of("op1", operator));
        Trainer trainer = trainer(updater, createMockEvaluator(0.6d)).numParallel(1).build();

        BaseAgent result = trainer.train(agent, createCaseLoader(), createCaseLoader(), 2, Map.of());

        assertSame(agent, result);
        assertEquals(2, updater.updateCount);
        assertEquals("v1", operator.parameters.get("param"));
    }

    @Test
    void checkpointDirEnablesCheckpointing(@TempDir Path tempDir) {
        RecordingUpdater updater = createMockUpdater();
        Trainer trainer = trainer(updater, createMockEvaluator(0.5d))
                .earlyStopScore(1.0d)
                .numParallel(1)
                .checkpointDir(tempDir.toString())
                .build();

        BaseAgent agent = createMockAgent();
        BaseAgent result = trainer.train(agent, createCaseLoader(), createCaseLoader(), 2, Map.of());

        assertSame(agent, result);
        assertEquals(1, updater.bindCount);
        assertNotNull(trainer.getCheckpointStore());
    }

    @Test
    void forwardWithEmptyCasesReturnsZeros() {
        Trainer.ForwardResult result = trainer(createMockUpdater(), createMockEvaluator(0.8d))
                .build()
                .forward(createMockAgent(), new CaseLoader(List.of()));

        assertEquals(0.0d, result.score());
        assertTrue(result.evaluatedCases().isEmpty());
        assertTrue(result.trajectories().isEmpty());
        assertTrue(result.sessions().isEmpty());
    }

    @Test
    void forwardWithNullCasesReturnsZeros() {
        Trainer.ForwardResult result = trainer(createMockUpdater(), createMockEvaluator(0.8d))
                .build()
                .forward(createMockAgent(), null);

        assertEquals(0.0d, result.score());
        assertTrue(result.evaluatedCases().isEmpty());
        assertTrue(result.trajectories().isEmpty());
        assertTrue(result.sessions().isEmpty());
    }

    @Test
    void evaluateWithEmptyCasesReturnsZeros() {
        Trainer.EvaluationResult result = trainer(createMockUpdater(), createMockEvaluator(0.8d))
                .build()
                .evaluate(createMockAgent(), new CaseLoader(List.of()));

        assertEquals(0.0d, result.score());
        assertTrue(result.evaluatedCases().isEmpty());
    }

    @Test
    void evaluateWithNullCasesReturnsZeros() {
        Trainer.EvaluationResult result = trainer(createMockUpdater(), createMockEvaluator(0.8d))
                .build()
                .evaluate(createMockAgent(), null);

        assertEquals(0.0d, result.score());
        assertTrue(result.evaluatedCases().isEmpty());
    }

    @Test
    void predictOnlyWithNullCasesReturnsEmptyList() {
        List<Map<String, Object>> result = trainer(createMockUpdater(), createMockEvaluator(0.8d))
                .build()
                .predictOnly(createMockAgent(), null);

        assertTrue(result.isEmpty());
    }

    @Test
    void predictWithEmptyCasesReturnsEmptyPair() {
        Trainer.PredictionResult result = trainer(createMockUpdater(), createMockEvaluator(0.8d))
                .build()
                .predict(createMockAgent(), new CaseLoader(List.of()));

        assertTrue(result.predictions().isEmpty());
        assertTrue(result.sessions().isEmpty());
    }

    @Test
    void trainWithCandidatesList() {
        RecordingUpdater updater = createMockUpdater();
        Updates candidate = Updates.of("op1", "param", "candidate1");
        updater.updateReturn = List.of(candidate);
        Trainer trainer = trainer(updater, createMockEvaluator(0.6d)).numParallel(1).build();
        RecordingOperator operator = new RecordingOperator("op1");
        TestAgent agent = createMockAgent(Map.of("op1", operator));

        BaseAgent result = trainer.train(agent, createCaseLoader(), createCaseLoader(), 2, Map.of());

        assertSame(agent, result);
        assertEquals(2, updater.updateCount);
        assertEquals("candidate1", operator.parameters.get("param"));
    }

    @Test
    void allCallbacksAreInvoked() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        Trainer trainer = trainer(createMockUpdater(), createMockEvaluator(0.5d))
                .callbacks(callbacks)
                .numParallel(1)
                .build();

        trainer.train(createMockAgent(), createCaseLoader(), createCaseLoader(), 2, Map.of());

        assertEquals(1, callbacks.trainBeginCount);
        assertTrue(callbacks.epochBeginCount > 0);
        assertTrue(callbacks.epochEndCount > 0);
        assertEquals(1, callbacks.trainEndCount);
    }

    @Test
    void epochCallbackReceivesProgress() {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        Trainer trainer = trainer(createMockUpdater(), createMockEvaluator(0.5d))
                .callbacks(callbacks)
                .numParallel(1)
                .build();

        trainer.train(createMockAgent(), createCaseLoader(), createCaseLoader(), 2, Map.of());

        assertNotNull(callbacks.lastEpochEndProgress);
        assertTrue(callbacks.lastEpochEndProgress.getCurrentEpochScore() >= 0.0d);
        assertTrue(callbacks.lastEpochEndProgress.getBestScore() >= 0.0d);
    }

    private static RecordingUpdater createMockUpdater() {
        return new RecordingUpdater();
    }

    private static RecordingEvaluator createMockEvaluator(double score) {
        return new RecordingEvaluator(score);
    }

    private static TestAgent createMockAgent() {
        return createMockAgent(Map.of(
                "llm_op", new RecordingOperator("llm_op"),
                "tool_op", new RecordingOperator("tool_op")
        ));
    }

    private static TestAgent createMockAgent(Map<String, Operator> operators) {
        return new TestAgent(operators);
    }

    private static CaseLoader createCaseLoader() {
        return createCaseLoader(3);
    }

    private static CaseLoader createCaseLoader(int caseCount) {
        List<Case> cases = new ArrayList<>();
        for (int index = 0; index < caseCount; index++) {
            cases.add(new Case(
                    Map.of("query", "question " + index),
                    Map.of("answer", "expected"),
                    null,
                    "case_" + index
            ));
        }
        return new CaseLoader(cases);
    }

    private static EvaluatedCase createEvaluatedCase() {
        return createEvaluatedCase(0.8d);
    }

    private static EvaluatedCase createEvaluatedCase(double score) {
        return new EvaluatedCase(
                new Case(Map.of("q", "test"), Map.of("ans", "expected")),
                Map.of("output", "pred"),
                score,
                "Good answer",
                null
        );
    }

    private static Trainer.Builder trainer(RecordingUpdater updater, RecordingEvaluator evaluator) {
        return Trainer.builder().updater(updater).evaluator(evaluator);
    }

    private static List<Integer> toList(Iterable<Integer> iterable) {
        List<Integer> values = new ArrayList<>();
        for (Integer value : iterable) {
            values.add(value);
        }
        return values;
    }

    /**
     * Mirrors Python's {@code create_mock_updater} helper in
     * {@code tests/unit_tests/agent_evolving/trainer/test_trainer.py}.
     */
    private static final class RecordingUpdater implements Updater {
        private int bindReturn = 1;
        private int bindCount;
        private int updateCount;
        private boolean requiresForward = true;
        private Object updateReturn = new Updates();

        @Override
        public int bind(Map<String, Operator> operators, List<String> targets, Map<String, Object> config) {
            bindCount += 1;
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
            updateCount += 1;
            return CompletableFuture.completedFuture(updateReturn);
        }

        @Override
        public CompletionStage<Object> process(
                List<Trajectory> trajectories,
                List<EvolutionSignal> signals,
                Map<String, Object> config) {
            return CompletableFuture.completedFuture(updateReturn);
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of();
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }
    }

    /**
     * Mirrors Python's {@code create_mock_evaluator} helper in
     * {@code tests/unit_tests/agent_evolving/trainer/test_trainer.py}.
     */
    private static final class RecordingEvaluator extends BaseEvaluator {
        private final double score;
        private int batchCount;
        private int lastNumParallel;

        private RecordingEvaluator(double score) {
            this.score = score;
        }

        @Override
        public EvaluatedCase evaluate(Case caseValue, Map<String, Object> predict) {
            return new EvaluatedCase(caseValue, predict, score, "", null);
        }

        @Override
        public List<EvaluatedCase> batchEvaluate(List<Case> cases, List<Map<String, Object>> predicts, int numParallel) {
            batchCount += 1;
            lastNumParallel = numParallel;
            List<EvaluatedCase> evaluated = new ArrayList<>();
            for (int index = 0; index < cases.size(); index++) {
                evaluated.add(evaluate(cases.get(index), predicts.get(index)));
            }
            return evaluated;
        }
    }

    /**
     * Mirrors Python's mock operators produced by {@code create_mock_agent} in
     * {@code tests/unit_tests/agent_evolving/trainer/test_trainer.py}.
     */
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

    /**
     * Mirrors Python's mock agent from {@code create_mock_agent} in
     * {@code tests/unit_tests/agent_evolving/trainer/test_trainer.py}.
     */
    private static final class TestAgent extends BaseAgent {
        private final Map<String, Operator> operators;
        private boolean failInvoke;

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
            if (failInvoke) {
                CompletableFuture<Object> failed = new CompletableFuture<>();
                failed.completeExceptionally(new RuntimeException("test error"));
                return failed;
            }
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

    /**
     * Mirrors Python's custom callback test helpers in
     * {@code tests/unit_tests/agent_evolving/trainer/test_trainer.py}.
     */
    private static final class RecordingCallbacks extends Callbacks {
        private int trainBeginCount;
        private int trainEndCount;
        private int epochBeginCount;
        private int epochEndCount;
        private Progress lastEpochEndProgress;

        @Override
        public void onTrainBegin(BaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
            trainBeginCount += 1;
        }

        @Override
        public void onTrainEnd(BaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
            trainEndCount += 1;
        }

        @Override
        public void onTrainEpochBegin(BaseAgent agent, Progress progress) {
            epochBeginCount += 1;
        }

        @Override
        public void onTrainEpochEnd(BaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
            epochEndCount += 1;
            lastEpochEndProgress = progress;
        }
    }
}
