package com.openjiuwen.agentevolving.trainer;

import com.openjiuwen.agentevolving.checkpointing.EvolveCheckpoint;
import com.openjiuwen.agentevolving.checkpointing.FileCheckpointStore;
import com.openjiuwen.agentevolving.dataset.Case;
import com.openjiuwen.agentevolving.dataset.CaseLoader;
import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.agentevolving.evaluator.BaseEvaluator;
import com.openjiuwen.agentevolving.trajectory.ExecutionSpec;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.TracerTrajectoryExtractor;
import com.openjiuwen.agentevolving.trajectory.Updates;
import com.openjiuwen.agentevolving.updater.Updater;
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
        assertEquals("case_0", forward.trajectories().get(0).getCaseId());
        assertEquals(2, forward.sessions().size());
    }

    @Test
    void predictOnlyReturnsPredictionsAndHandlesInvokeException() {
        Trainer trainer = builder(new RecordingUpdater(), new MatchingEvaluator()).build();
        FakeAgent agent = new FakeAgent(Map.of("op_1", new FakeOperator("op_1", "expected")));
        agent.throwOnInvoke = true;

        List<Map<String, Object>> predictions = trainer.predictOnly(agent, loader("expected"));

        assertEquals(1, predictions.size());
        assertTrue(String.valueOf(predictions.get(0).get("error")).contains("boom"));
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
        assertNotEquals("case_custom", prediction.predictions().get(0).get("session_id"));
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
        private final Deque<Object> scriptedUpdates = new ArrayDeque<>();
        private int bindCount = 1;
        private boolean requiresForward = true;
        private int updateCalls;
        private int lastTrajectoryCount;
        private int lastEvaluatedCount;
        private Map<String, Object> loadedState = Map.of();

        @Override
        public int bind(Map<String, Object> operators, List<String> targets, Map<String, Object> config) {
            return bindCount;
        }

        @Override
        public boolean requiresForwardData() {
            return requiresForward;
        }

        @Override
        public Object update(List<com.openjiuwen.agentevolving.trajectory.Trajectory> trajectories,
                             List<Object> evaluatedCases,
                             Map<String, Object> config) {
            updateCalls++;
            lastTrajectoryCount = trajectories != null ? trajectories.size() : -1;
            lastEvaluatedCount = evaluatedCases != null ? evaluatedCases.size() : -1;
            return scriptedUpdates.isEmpty() ? new Updates() : scriptedUpdates.removeFirst();
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
        @Override
        public EvaluatedCase evaluate(Case caseData, Map<String, Object> predict) {
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
