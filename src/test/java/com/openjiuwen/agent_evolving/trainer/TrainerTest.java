package com.openjiuwen.agent_evolving.trainer;

import com.openjiuwen.agent_evolving.checkpointing.EvolveCheckpoint;
import com.openjiuwen.agent_evolving.checkpointing.FileCheckpointStore;
import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.CaseLoader;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.evaluator.BaseEvaluator;
import com.openjiuwen.agent_evolving.trajectory.ExecutionSpec;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TracerTrajectoryExtractor;
import com.openjiuwen.agent_evolving.trajectory.UpdateKey;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.agent_evolving.updater.Updater;
import com.openjiuwen.core.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainerTest {

    @Test
    void predictEvaluateAndForwardReturnTypedResults() {
        RecordingUpdater updater = new RecordingUpdater();
        MatchingEvaluator evaluator = new MatchingEvaluator();
        Trainer trainer = builder(updater, evaluator).build();
        FakeAgent agent = new FakeAgent(Map.of("op_1", new FakeOperator("op_1", "expected")));
        CaseLoader cases = loader("expected", "expected");

        PredictionResult prediction = trainer.predict(agent, cases);
        EvaluationResult evaluation = trainer.evaluate(agent, cases);
        ForwardResult forward = trainer.forward(agent, cases);

        assertEquals(2, prediction.predictions().size());
        assertEquals(2, prediction.sessions().size());
        assertEquals(1.0, evaluation.score());
        assertEquals(2, evaluation.evaluatedCases().size());
        assertEquals(1.0, forward.score());
        assertEquals(2, forward.evaluatedCases().size());
        assertEquals(2, forward.trajectories().size());
        assertEquals("case_0", forward.trajectories().getFirst().getCaseId());
        assertEquals(2, forward.sessions().size());
    }

    @Test
    void predictOnlyReturnsPredictionsAndHandlesInvokeException() {
        Trainer trainer = builder(new RecordingUpdater(), new MatchingEvaluator()).build();
        FakeAgent agent = new FakeAgent(Map.of("op_1", new FakeOperator("op_1", "expected")));
        agent.throwOnInvoke = true;

        List<Map<String, Object>> predictions = trainer.predictOnly(agent, loader("expected"));

        assertEquals(1, predictions.size());
        assertTrue(String.valueOf(predictions.getFirst().get("error")).contains("boom"));
    }

    @Test
    void predictPassesConversationIdAndUsesFreshSessionIds() {
        Trainer trainer = new Trainer.Builder()
                .updater(new RecordingUpdater())
                .evaluator(new MatchingEvaluator())
                .numParallel(1)
                .build();
        FakeAgent agent = new FakeAgent(Map.of("op_1", new FakeOperator("op_1", "expected")));
        CaseLoader cases = new CaseLoader(List.of(
                new Case(Map.of("query", "question_0"), Map.of("answer", "expected"), "case_custom"),
                new Case(Map.of("query", "question_1"), Map.of("answer", "expected"), "")
        ));

        PredictionResult prediction = trainer.predict(agent, cases);

        assertEquals("case_custom", agent.invocationInputs.get(0).get("conversation_id"));
        assertTrue(agent.invocationInputs.get(1).containsKey("conversation_id"));
        assertEquals("", agent.invocationInputs.get(1).get("conversation_id"));
        assertNotEquals("case_custom", prediction.predictions().getFirst().get("session_id"));
        assertTrue(String.valueOf(prediction.predictions().get(1).get("session_id")).length() > 0);
    }

    @Test
    void setCallbacksIsUsedDuringTraining() {
        RecordingUpdater updater = new RecordingUpdater();
        MatchingEvaluator evaluator = new MatchingEvaluator();
        Trainer trainer = builder(updater, evaluator).build();
        TrackingCallbacks callbacks = new TrackingCallbacks();
        trainer.setCallbacks(callbacks);

        FakeAgent agent = new FakeAgent(Map.of("op_1", new FakeOperator("op_1", "base")));
        Object trained = trainer.train(agent, loader("mismatch"), loader("mismatch"), 2, Map.of());

        assertSame(agent, trained);
        assertEquals(1, callbacks.trainBeginCalls);
        assertEquals(1, callbacks.trainEndCalls);
        assertEquals(2, callbacks.epochBeginCalls);
        assertEquals(2, callbacks.epochEndCalls);
    }

    @Test
    void trainStopsEarlyWhenBaselineMeetsThreshold() {
        RecordingUpdater updater = new RecordingUpdater();
        MatchingEvaluator evaluator = new MatchingEvaluator();
        Trainer trainer = new Trainer.Builder()
                .updater(updater)
                .evaluator(evaluator)
                .earlyStopScore(1.0)
                .build();
        FakeAgent agent = new FakeAgent(Map.of("op_1", new FakeOperator("op_1", "expected")));

        Object trained = trainer.train(agent, loader("expected"), loader("expected"), 5, Map.of());

        assertSame(agent, trained);
        assertEquals(0, updater.updateCalls);
    }

    @Test
    void trainSelectsBestCandidateAndRestoresWinningState() {
        RecordingUpdater updater = new RecordingUpdater();
        updater.scriptedUpdates.add(List.of(
                Updates.of("op_1", "system_prompt", "bad"),
                Updates.of("op_1", "system_prompt", "good")
        ));
        MatchingEvaluator evaluator = new MatchingEvaluator();
        Trainer trainer = builder(updater, evaluator).build();
        FakeOperator operator = new FakeOperator("op_1", "base");
        FakeAgent agent = new FakeAgent(Map.of("op_1", operator));

        Object trained = trainer.train(agent, loader("good"), loader("good"), 1, Map.of());

        assertSame(agent, trained);
        assertEquals("good", operator.parameterValue);
        assertEquals(1, updater.updateCalls);
    }

    @Test
    void trainSupportsBlackBoxUpdaterWithoutForwardData() {
        RecordingUpdater updater = new RecordingUpdater();
        updater.requiresForward = false;
        updater.scriptedUpdates.add(List.of(Updates.of("op_1", "system_prompt", "good")));
        MatchingEvaluator evaluator = new MatchingEvaluator();
        Trainer trainer = builder(updater, evaluator).build();
        FakeOperator operator = new FakeOperator("op_1", "base");
        FakeAgent agent = new FakeAgent(Map.of("op_1", operator));

        Object trained = trainer.train(agent, loader("good"), loader("good"), 2, Map.of());

        assertSame(agent, trained);
        assertEquals(1, updater.updateCalls);
        assertEquals(0, updater.lastTrajectoryCount);
        assertEquals(0, updater.lastEvaluatedCount);
        assertEquals("good", operator.parameterValue);
    }

    @Test
    void forwardPropagatesTrajectoryExtractionErrors() {
        Trainer trainer = new Trainer.Builder()
                .updater(new RecordingUpdater())
                .evaluator(new MatchingEvaluator())
                .extractor(new TracerTrajectoryExtractor() {
                    @Override
                    public Trajectory extract(Object session, ExecutionSpec execution) {
                        throw new IllegalStateException("extract boom");
                    }
                })
                .build();
        FakeAgent agent = new FakeAgent(Map.of("op_1", new FakeOperator("op_1", "expected")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> trainer.forward(agent, loader("expected"))
        );

        assertTrue(String.valueOf(error.getMessage()).contains("extract boom"));
    }

    @Test
    void resumeRestoresStartEpochWithoutChangingCurrentEpoch(@TempDir Path tempDir) {
        RecordingUpdater updater = new RecordingUpdater();
        MatchingEvaluator evaluator = new MatchingEvaluator();
        ResumeTrackingCallbacks callbacks = new ResumeTrackingCallbacks();
        String resumePath = new FileCheckpointStore(tempDir.toString()).saveCheckpoint(
                EvolveCheckpoint.builder()
                        .version("v1")
                        .runId("resume-run")
                        .step(Map.of("epoch", 2, "batch", 0))
                        .best(Map.of("best_score", 0.75))
                        .operatorsState(Map.of("op_1", Map.of("system_prompt", "restored")))
                        .updaterState(Map.of("marker", "resume"))
                        .searcherState(Map.of())
                        .lastMetrics(Map.of("current_epoch_score", 0.4))
                        .build(),
                "resume.json"
        );
        Trainer trainer = new Trainer.Builder()
                .updater(updater)
                .evaluator(evaluator)
                .callbacks(callbacks)
                .checkpointDir(tempDir.toString())
                .resumeFrom(resumePath)
                .build();
        FakeOperator operator = new FakeOperator("op_1", "base");
        FakeAgent agent = new FakeAgent(Map.of("op_1", operator));

        Object trained = trainer.train(agent, loader("restored"), loader("restored"), 3, Map.of());

        assertSame(agent, trained);
        assertEquals("restored", operator.parameterValue);
        assertEquals(Map.of("marker", "resume"), updater.loadedState);
        assertEquals(2, callbacks.observedStartEpoch);
        assertEquals(0, callbacks.observedCurrentEpoch);
        assertEquals(1.0, callbacks.observedBestScore);
    }

    @Test
    void trainAppliesDirectUpdatesReturnedAsUpdates() {
        RecordingUpdater updater = new RecordingUpdater();
        updater.scriptedUpdates.add(Updates.of("op_1", "system_prompt", "updated"));
        MatchingEvaluator evaluator = new MatchingEvaluator();
        FakeOperator operator = new FakeOperator("op_1", "base");
        FakeAgent agent = new FakeAgent(Map.of("op_1", operator));

        Object trained = builder(updater, evaluator).build()
                .train(agent, loader("updated"), loader("updated"), 1, Map.of());

        assertSame(agent, trained);
        assertEquals("updated", operator.parameterValue);
    }

    @Test
    void trainAppliesDirectUpdatesReturnedAsPlainMap() {
        RecordingUpdater updater = new RecordingUpdater();
        Map<UpdateKey, Object> rawUpdates = new LinkedHashMap<>();
        rawUpdates.put(UpdateKey.of("op_1", "system_prompt"), "updated");
        updater.scriptedUpdates.add(rawUpdates);
        MatchingEvaluator evaluator = new MatchingEvaluator();
        FakeOperator operator = new FakeOperator("op_1", "base");
        FakeAgent agent = new FakeAgent(Map.of("op_1", operator));

        Object trained = builder(updater, evaluator).build()
                .train(agent, loader("updated"), loader("updated"), 1, Map.of());

        assertSame(agent, trained);
        assertEquals("updated", operator.parameterValue);
    }

    @Test
    void trainTreatsNullUpdaterResultAsEmptyUpdates() {
        RecordingUpdater updater = new RecordingUpdater();
        updater.scriptedUpdates.add(RecordingUpdater.NULL_UPDATE);
        FakeOperator operator = new FakeOperator("op_1", "base");
        FakeAgent agent = new FakeAgent(Map.of("op_1", operator));

        Object trained = builder(updater, new MatchingEvaluator()).build()
                .train(agent, loader("mismatch"), loader("mismatch"), 1, Map.of());

        assertSame(agent, trained);
        assertEquals("base", operator.parameterValue);
    }

    @Test
    void trainAcceptsCandidateReturnedAsPlainMap() {
        RecordingUpdater updater = new RecordingUpdater();
        Map<UpdateKey, Object> rawCandidate = new LinkedHashMap<>();
        rawCandidate.put(UpdateKey.of("op_1", "system_prompt"), "good");
        updater.scriptedUpdates.add(List.of(rawCandidate));
        MatchingEvaluator evaluator = new MatchingEvaluator();
        FakeOperator operator = new FakeOperator("op_1", "base");
        FakeAgent agent = new FakeAgent(Map.of("op_1", operator));

        Object trained = builder(updater, evaluator).build()
                .train(agent, loader("good"), loader("good"), 1, Map.of());

        assertSame(agent, trained);
        assertEquals("good", operator.parameterValue);
    }

    @Test
    void trainFallsBackToValidationWhenCandidateListIsEmpty() {
        RecordingUpdater updater = new RecordingUpdater();
        updater.scriptedUpdates.add(List.of());
        MatchingEvaluator evaluator = new MatchingEvaluator();
        FakeOperator operator = new FakeOperator("op_1", "base");
        FakeAgent agent = new FakeAgent(Map.of("op_1", operator));

        Object trained = builder(updater, evaluator).build()
                .train(agent, loader("base"), loader("base"), 1, Map.of());

        assertSame(agent, trained);
        assertEquals("base", operator.parameterValue);
        assertTrue(evaluator.evaluateCalls > 0);
    }

    @Test
    void trainUsesSnakeCaseOperatorGetterFallback() {
        RecordingUpdater updater = new RecordingUpdater();
        MatchingEvaluator evaluator = new MatchingEvaluator();
        FakeOperator operator = new FakeOperator("op_1", "expected");
        SnakeCaseAgent agent = new SnakeCaseAgent(Map.of("op_1", operator));

        Object trained = builder(updater, evaluator).build()
                .train(agent, loader("expected"), loader("expected"), 1, Map.of());

        assertSame(agent, trained);
        assertEquals(List.of("op_1"), new ArrayList<>(updater.lastBoundOperators.keySet()));
    }

    @Test
    void trainBindsEmptyRegistryWhenOperatorGetterIsMissing() {
        RecordingUpdater updater = new RecordingUpdater();
        updater.bindCount = 0;
        GetterlessAgent agent = new GetterlessAgent();

        Object trained = builder(updater, new MatchingEvaluator()).build()
                .train(agent, loader("expected"), loader("expected"), 1, Map.of());

        assertSame(agent, trained);
        assertTrue(updater.lastBoundOperators.isEmpty());
    }

    @Test
    void trainBindsEmptyRegistryWhenOperatorGetterThrows() {
        RecordingUpdater updater = new RecordingUpdater();
        updater.bindCount = 0;
        ThrowingGetterAgent agent = new ThrowingGetterAgent();

        Object trained = builder(updater, new MatchingEvaluator()).build()
                .train(agent, loader("expected"), loader("expected"), 1, Map.of());

        assertSame(agent, trained);
        assertTrue(updater.lastBoundOperators.isEmpty());
    }

    @Test
    void trainRunsConfiguredNumberOfIterationsWithoutEarlyStop() {
        RecordingUpdater updater = new RecordingUpdater();
        MatchingEvaluator evaluator = new MatchingEvaluator();
        FakeAgent agent = new FakeAgent(Map.of("op_1", new FakeOperator("op_1", "base")));

        Object trained = builder(updater, evaluator).build()
                .train(agent, loader("mismatch"), loader("mismatch"), 3, Map.of());

        assertSame(agent, trained);
        assertEquals(3, updater.updateCalls);
    }

    @Test
    void trainUsesTrainCasesAsValidationWhenValCasesIsNull() {
        RecordingUpdater updater = new RecordingUpdater();
        MatchingEvaluator evaluator = new MatchingEvaluator();
        FakeAgent agent = new FakeAgent(Map.of("op_1", new FakeOperator("op_1", "base")));

        Object trained = builder(updater, evaluator).build()
                .train(agent, loader("base", "base"), null, 1, Map.of());

        assertSame(agent, trained);
        assertTrue(evaluator.evaluatedCaseIds.contains("case_0"));
        assertTrue(evaluator.evaluatedCaseIds.contains("case_1"));
    }

    @Test
    void builderRequiresUpdater() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new Trainer.Builder().evaluator(new MatchingEvaluator()).build()
        );

        assertEquals("updater is required", error.getMessage());
    }

    @Test
    void builderRequiresEvaluator() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new Trainer.Builder().updater(new RecordingUpdater()).build()
        );

        assertEquals("evaluator is required", error.getMessage());
    }

    @Test
    void predictWithNullCasesReturnsEmptyResult() {
        PredictionResult result = builder(new RecordingUpdater(), new MatchingEvaluator()).build()
                .predict(new GetterlessAgent(), null);

        assertEquals(List.of(), result.predictions());
        assertEquals(List.of(), result.sessions());
    }

    @Test
    void forwardWithNullCasesReturnsEmptyResult() {
        ForwardResult result = builder(new RecordingUpdater(), new MatchingEvaluator()).build()
                .forward(new GetterlessAgent(), null);

        assertEquals(0.0, result.score());
        assertEquals(List.of(), result.evaluatedCases());
        assertEquals(List.of(), result.trajectories());
        assertEquals(List.of(), result.sessions());
    }

    @Test
    void evaluateWithNullCasesReturnsEmptyResult() {
        EvaluationResult result = builder(new RecordingUpdater(), new MatchingEvaluator()).build()
                .evaluate(new GetterlessAgent(), null);

        assertEquals(0.0, result.score());
        assertEquals(List.of(), result.evaluatedCases());
    }

    @Test
    void predictOnlyWithNullCasesReturnsEmptyList() {
        List<Map<String, Object>> predictions = builder(new RecordingUpdater(), new MatchingEvaluator()).build()
                .predictOnly(new GetterlessAgent(), null);

        assertEquals(List.of(), predictions);
    }

    @Test
    void predictWithEmptyCasesReturnsEmptyResult() {
        PredictionResult result = builder(new RecordingUpdater(), new MatchingEvaluator()).build()
                .predict(new GetterlessAgent(), new CaseLoader(List.of()));

        assertEquals(List.of(), result.predictions());
        assertEquals(List.of(), result.sessions());
    }

    @Test
    void applyUpdatesSkipsNullInputs() {
        Trainer.applyUpdates(null, Updates.of("op_1", "system_prompt", "ignored"));
        Trainer.applyUpdates(Map.of("op_1", new FakeOperator("op_1", "base")), null);
    }

    @Test
    void applyUpdatesSkipsMissingOperatorAndNullValue() {
        FakeOperator operator = new FakeOperator("op_1", "base");
        Updates updates = new Updates();
        updates.put("missing", "system_prompt", "ignored");
        updates.put("op_1", "system_prompt", null);

        Trainer.applyUpdates(Map.of("op_1", operator), updates);

        assertEquals("base", operator.parameterValue);
    }

    @Test
    void applyUpdatesAppliesMultipleOperators() {
        FakeOperator first = new FakeOperator("op_1", "base1");
        FakeOperator second = new FakeOperator("op_2", "base2");
        Updates updates = new Updates();
        updates.put("op_1", "system_prompt", "v1");
        updates.put("op_2", "system_prompt", "v2");

        Trainer.applyUpdates(Map.of("op_1", first, "op_2", second), updates);

        assertEquals("v1", first.parameterValue);
        assertEquals("v2", second.parameterValue);
    }

    @Test
    void predictWrapsNonMapResultAsOutput() {
        RawAgent agent = new RawAgent("raw-output");

        PredictionResult result = builder(new RecordingUpdater(), new MatchingEvaluator()).build()
                .predict(agent, loader("raw-output"));

        assertEquals("raw-output", result.predictions().getFirst().get("output"));
    }

    @Test
    void predictNullAgentProducesErrorPrediction() {
        PredictionResult result = builder(new RecordingUpdater(), new MatchingEvaluator()).build()
                .predict(null, loader("expected"));

        assertTrue(String.valueOf(result.predictions().getFirst().get("error")).contains("Get wrong result due to"));
    }

    private static Trainer.Builder builder(Updater updater, BaseEvaluator evaluator) {
        return new Trainer.Builder()
                .updater(updater)
                .evaluator(evaluator)
                .numParallel(2);
    }

    private static CaseLoader loader(String... answers) {
        List<Case> cases = new ArrayList<>();
        for (int i = 0; i < answers.length; i++) {
            cases.add(new Case(
                    Map.of("query", "question_" + i),
                    Map.of("answer", answers[i]),
                    "case_" + i
            ));
        }
        return new CaseLoader(cases);
    }

    private static final class FakeAgent {
        private final Map<String, Object> operators;
        private final List<Map<String, Object>> invocationInputs = new ArrayList<>();
        private boolean throwOnInvoke;

        private FakeAgent(Map<String, Object> operators) {
            this.operators = new LinkedHashMap<>(operators);
        }

        public Map<String, Object> getOperators() {
            return operators;
        }

        public Map<String, Object> invoke(Object input, Session session) {
            if (throwOnInvoke) {
                throw new IllegalStateException("boom");
            }
            if (input instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedInput = (Map<String, Object>) map;
                invocationInputs.add(new LinkedHashMap<>(typedInput));
            }
            FakeOperator operator = (FakeOperator) operators.get("op_1");
            return Map.of(
                    "output", operator.parameterValue,
                    "session_id", session != null ? session.getSessionId() : "none"
            );
        }
    }

    private static final class SnakeCaseAgent {
        private final Map<String, Object> operators;

        private SnakeCaseAgent(Map<String, Object> operators) {
            this.operators = new LinkedHashMap<>(operators);
        }

        public Map<String, Object> get_operators() {
            return operators;
        }

        public Map<String, Object> invoke(Object input, Session session) {
            FakeOperator operator = (FakeOperator) operators.get("op_1");
            return Map.of("output", operator.parameterValue);
        }
    }

    private static final class GetterlessAgent {
        public Map<String, Object> invoke(Object input, Session session) {
            return Map.of("output", "unused");
        }
    }

    private static final class ThrowingGetterAgent {
        public Map<String, Object> getOperators() {
            throw new IllegalStateException("getter boom");
        }

        public Map<String, Object> invoke(Object input, Session session) {
            return Map.of("output", "unused");
        }
    }

    private static final class RawAgent {
        private final Object result;

        private RawAgent(Object result) {
            this.result = result;
        }

        public Object invoke(Object input, Session session) {
            return result;
        }
    }

    private static final class FakeOperator {
        private final String operatorId;
        private String parameterValue;

        private FakeOperator(String operatorId, String parameterValue) {
            this.operatorId = operatorId;
            this.parameterValue = parameterValue;
        }

        public String getOperatorId() {
            return operatorId;
        }

        public void setParameter(String target, Object value) {
            if ("system_prompt".equals(target) && value != null) {
                parameterValue = String.valueOf(value);
            }
        }

        public Map<String, Object> getState() {
            return new LinkedHashMap<>(Map.of("system_prompt", parameterValue));
        }

        public void loadState(Map<String, Object> state) {
            Object value = state.get("system_prompt");
            parameterValue = value != null ? String.valueOf(value) : parameterValue;
        }
    }

    private static final class RecordingUpdater implements Updater {
        private static final Object NULL_UPDATE = new Object();

        private final Deque<Object> scriptedUpdates = new ArrayDeque<>();
        private int bindCount = 1;
        private boolean requiresForward = true;
        private int updateCalls;
        private int lastTrajectoryCount;
        private int lastEvaluatedCount;
        private Map<String, Object> loadedState = Map.of();
        private Map<String, Object> lastBoundOperators = Map.of();

        @Override
        public int bind(Map<String, Object> operators, List<String> targets, Map<String, Object> config) {
            lastBoundOperators = operators != null ? new LinkedHashMap<>(operators) : Map.of();
            return bindCount;
        }

        @Override
        public boolean requiresForwardData() {
            return requiresForward;
        }

        @Override
        public Object update(List<com.openjiuwen.agent_evolving.trajectory.Trajectory> trajectories,
                             List<Object> evaluatedCases,
                             Map<String, Object> config) {
            updateCalls++;
            lastTrajectoryCount = trajectories != null ? trajectories.size() : -1;
            lastEvaluatedCount = evaluatedCases != null ? evaluatedCases.size() : -1;
            Object next = scriptedUpdates.isEmpty() ? new Updates() : scriptedUpdates.removeFirst();
            return next == NULL_UPDATE ? null : next;
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of();
        }

        @Override
        public void loadState(Map<String, Object> state) {
            loadedState = state != null ? new LinkedHashMap<>(state) : Map.of();
        }
    }

    private static final class MatchingEvaluator extends BaseEvaluator {
        private int evaluateCalls;
        private final List<String> evaluatedCaseIds = new ArrayList<>();

        @Override
        public EvaluatedCase evaluate(Case caseData, Map<String, Object> predict) {
            evaluateCalls++;
            evaluatedCaseIds.add(caseData.getCaseId());
            boolean matched = String.valueOf(caseData.getLabel().get("answer"))
                    .equals(String.valueOf(predict.get("output")));
            return EvaluatedCase.builder()
                    .caseData(caseData)
                    .answer(predict)
                    .score(matched ? 1.0 : 0.0)
                    .reason(matched ? "match" : "mismatch")
                    .build();
        }
    }

    private static final class TrackingCallbacks extends Callbacks {
        private int trainBeginCalls;
        private int trainEndCalls;
        private int epochBeginCalls;
        private int epochEndCalls;

        @Override
        public void onTrainBegin(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
            trainBeginCalls++;
            assertNotNull(progress);
            assertNotNull(evalInfo);
        }

        @Override
        public void onTrainEnd(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
            trainEndCalls++;
            assertNotNull(progress);
            assertNotNull(evalInfo);
        }

        @Override
        public void onTrainEpochBegin(Object agent, Progress progress) {
            epochBeginCalls++;
            assertNotNull(progress);
        }

        @Override
        public void onTrainEpochEnd(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
            epochEndCalls++;
            assertNotNull(progress);
            assertNotNull(evalInfo);
        }
    }

    private static final class ResumeTrackingCallbacks extends Callbacks {
        private int observedStartEpoch = -1;
        private int observedCurrentEpoch = -1;
        private double observedBestScore = -1.0;

        @Override
        public void onTrainBegin(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
            observedStartEpoch = progress.getStartEpoch();
            observedCurrentEpoch = progress.getCurrentEpoch();
            observedBestScore = progress.getBestScore();
        }
    }
}
