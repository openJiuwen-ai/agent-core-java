package com.openjiuwen.agent_evolving.trainer;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.CaseLoader;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.evaluator.BaseEvaluator;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.agent_evolving.updater.Updater;
import com.openjiuwen.core.session.Session;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        @Override
        public int bind(Map<String, Object> operators, List<String> targets, Map<String, Object> config) {
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
            return scriptedUpdates.isEmpty() ? new Updates() : scriptedUpdates.removeFirst();
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of();
        }

        @Override
        public void loadState(Map<String, Object> state) {
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
}
