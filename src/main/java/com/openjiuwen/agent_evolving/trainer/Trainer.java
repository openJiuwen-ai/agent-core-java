// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trainer;

import com.openjiuwen.agent_evolving.TuneConstant;
import com.openjiuwen.agent_evolving.TuneUtils;
import com.openjiuwen.agent_evolving.checkpointing.DefaultCheckpointManager;
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
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.internal.AgentSession;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Orchestrates "evaluate -> update -> writeback" self-evolution cycle.
 *
 * <p>Accepts Updater and BaseEvaluator, manages checkpoint/resume.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.trainer.trainer.Trainer}.
 */
public class Trainer {

    private final Updater updater;
    private final BaseEvaluator evaluator;
    private final TracerTrajectoryExtractor extractor;
    private Callbacks callbacks;
    private final int numParallel;
    private final double earlyStopScore;
    private final FileCheckpointStore checkpointStore;
    private final String resumeFrom;
    private final DefaultCheckpointManager checkpointManager;

    private Trainer(Builder builder) {
        this.updater = builder.updater;
        this.evaluator = builder.evaluator;
        this.extractor = builder.extractor != null ? builder.extractor : new TracerTrajectoryExtractor();
        this.callbacks = builder.callbacks != null ? builder.callbacks : new Callbacks();
        this.numParallel = builder.numParallel;
        this.earlyStopScore = builder.earlyStopScore;
        this.checkpointStore = builder.checkpointDir != null ? new FileCheckpointStore(builder.checkpointDir) : null;
        this.resumeFrom = builder.resumeFrom;
        this.checkpointManager = checkpointStore != null
                ? new DefaultCheckpointManager(null, "v1", builder.checkpointEveryNEpochs, builder.checkpointOnImprove)
                : null;
    }

    /**
     * Set training lifecycle callbacks.
     *
     * @param callbacks Callbacks instance
     */
    public void setCallbacks(Callbacks callbacks) {
        if (callbacks != null) {
            this.callbacks = callbacks;
        }
    }

    /**
     * Execute self-evolving training.
     *
     * @param agent         Agent to optimize
     * @param trainCases    Training case loader
     * @param valCases      Validation case loader
     * @param numIterations Maximum training epochs
     * @param kwargs        Additional configuration
     * @return Agent after training
     */
    public Object train(
            Object agent,
            CaseLoader trainCases,
            CaseLoader valCases,
            int numIterations,
            Map<String, Object> kwargs
    ) {
        Progress progress = new Progress(numIterations);
        CaseLoader effectiveValCases = valCases != null ? valCases : trainCases;
        Map<String, Object> config = kwargs != null ? kwargs : new LinkedHashMap<>();

        Map<String, Object> operators = getOperatorRegistry(agent);
        if (bindUpdater(operators, config) == 0) {
            Loggers.AGENT.error("[Trainer] no operator matches updater targets; soft-exit without training.");
            return agent;
        }

        resumeIfNeeded(agent, progress);

        List<EvaluatedCase> baselineEvaluated = List.of();
        if (updaterRequiresForward()) {
            EvaluationResult baseline = evaluate(agent, effectiveValCases);
            progress.setCurrentEpochScore(baseline.score());
            progress.setBestScore(Math.max(progress.getBestScore(), baseline.score()));
            baselineEvaluated = baseline.evaluatedCases();
        } else {
            progress.setCurrentEpochScore(0.0);
        }

        callbacks.onTrainBegin(agent, progress, baselineEvaluated);

        if (progress.getBestScore() >= earlyStopScore) {
            callbacks.onTrainEnd(agent, progress, baselineEvaluated);
            return agent;
        }

        for (int ignored : progress.runEpoch()) {
            callbacks.onTrainEpochBegin(agent, progress);

            ForwardResult forwardResult = updaterRequiresForward()
                    ? forward(agent, trainCases)
                    : new ForwardResult(0.0, List.of(), List.of(), List.of());
            progress.setCurrentEpochScore(forwardResult.score());

            Object updated = updater.update(
                    forwardResult.trajectories(),
                    new ArrayList<>(forwardResult.evaluatedCases()),
                    config
            );

            EvaluationResult validationResult;
            List<Updates> candidates = coerceCandidates(updated);
            if (candidates != null) {
                validationResult = selectBestCandidateOnVal(agent, operators, candidates, effectiveValCases);
            } else {
                applyUpdates(operators, updated instanceof Updates updates ? updates : new Updates());
                validationResult = evaluate(agent, effectiveValCases);
            }

            boolean improved = validationResult.score() > progress.getBestScore();
            if (improved) {
                progress.setBestScore(validationResult.score());
            }

            callbacks.onTrainEpochEnd(agent, progress, validationResult.evaluatedCases());

            saveCheckpointIfNeeded(agent, progress, improved);

            if (progress.getBestScore() >= earlyStopScore) {
                break;
            }
        }

        callbacks.onTrainEnd(agent, progress, baselineEvaluated);
        return agent;
    }

    /**
     * Single forward pass on cases.
     *
     * @param agent Agent instance
     * @param cases Case loader
     * @return Forward result with score, evaluated cases, trajectories, and sessions
     */
    public ForwardResult forward(Object agent, CaseLoader cases) {
        if (cases == null || cases.isEmpty()) {
            return new ForwardResult(0.0, List.of(), List.of(), List.of());
        }

        PredictionResult predictionResult = predict(agent, cases);
        List<Case> caseList = cases.getCases();
        List<EvaluatedCase> evaluated = evaluator.batchEvaluate(caseList, predictionResult.predictions(), numParallel);
        double score = meanScore(evaluated);

        List<Trajectory> trajectories = new ArrayList<>(caseList.size());
        for (int i = 0; i < caseList.size(); i++) {
            Case caseData = caseList.get(i);
            String executionId = UUID.randomUUID().toString();
            ExecutionSpec execution = ExecutionSpec.builder()
                    .caseId(caseData.getCaseId())
                    .executionId(executionId)
                    .build();
            Object session = predictionResult.sessions().get(i);
            trajectories.add(extractor.extract(session, execution));
        }

        return new ForwardResult(score, evaluated, trajectories, predictionResult.sessions());
    }

    /**
     * Run inference and evaluation on cases.
     *
     * @param agent Agent instance
     * @param cases Case loader
     * @return Evaluation result
     */
    public EvaluationResult evaluate(Object agent, CaseLoader cases) {
        if (cases == null || cases.isEmpty()) {
            return new EvaluationResult(0.0, List.of());
        }
        List<Map<String, Object>> predictions = predictOnly(agent, cases);
        List<EvaluatedCase> evaluated = evaluator.batchEvaluate(cases.getCases(), predictions, numParallel);
        return new EvaluationResult(meanScore(evaluated), evaluated);
    }

    /**
     * Run inference only, return model outputs per case.
     */
    public List<Map<String, Object>> predictOnly(Object agent, CaseLoader cases) {
        return predict(agent, cases).predictions();
    }

    /**
     * Run agent.invoke on each case and keep the execution sessions for trajectory extraction.
     */
    public PredictionResult predict(Object agent, CaseLoader cases) {
        if (cases == null || cases.isEmpty()) {
            return new PredictionResult(List.of(), List.of());
        }

        List<Case> caseList = cases.getCases();
        int workers = Math.max(1, Math.min(numParallel, caseList.size()));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<PredictionAndSession>> futures = new ArrayList<>(caseList.size());
            for (Case caseData : caseList) {
                futures.add(executor.submit(buildPredictionTask(agent, caseData)));
            }

            List<Map<String, Object>> predictions = new ArrayList<>(caseList.size());
            List<Object> sessions = new ArrayList<>(caseList.size());
            for (Future<PredictionAndSession> future : futures) {
                try {
                    PredictionAndSession item = future.get();
                    predictions.add(item.prediction());
                    sessions.add(item.session());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Prediction interrupted", e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new RuntimeException("Prediction failed", cause);
                }
            }
            return new PredictionResult(predictions, sessions);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Apply updater-generated updates to operator registry.
     */
    public static void applyUpdates(Map<String, Object> operators, Updates updates) {
        if (operators == null || operators.isEmpty() || updates == null || updates.isEmpty()) {
            return;
        }
        for (Map.Entry<UpdateKey, Object> entry : updates.entrySet()) {
            String operatorId = entry.getKey().getOperatorId();
            String target = entry.getKey().getTarget();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            Object operator = operators.get(operatorId);
            if (operator == null) {
                continue;
            }
            invokeIfPresent(operator, "setParameter", new Class<?>[]{String.class, Object.class}, target, value);
        }
    }

    private double meanScore(List<EvaluatedCase> evaluated) {
        if (evaluated == null || evaluated.isEmpty()) {
            return 0.0;
        }
        return evaluated.stream().mapToDouble(EvaluatedCase::getScore).average().orElse(0.0);
    }

    private int bindUpdater(Map<String, Object> operators, Map<String, Object> config) {
        return updater.bind(operators, null, config);
    }

    private boolean updaterRequiresForward() {
        return updater.requiresForwardData();
    }

    private void resumeIfNeeded(Object agent, Progress progress) {
        if (checkpointStore == null || checkpointManager == null || resumeFrom == null) {
            return;
        }
        EvolveCheckpoint checkpoint = checkpointStore.loadCheckpoint(resumeFrom);
        if (checkpoint == null) {
            return;
        }

        Map<String, Object> restored = checkpointManager.restore(agent, checkpoint);
        progress.setStartEpoch(intValue(restored, "start_epoch", intValue(restored, "startEpoch", 0)));
        progress.setBestScore(doubleValue(restored, "best_score", doubleValue(restored, "bestScore", 0.0)));

        Map<String, Object> updaterState = checkpoint.getUpdaterState() != null ? checkpoint.getUpdaterState() : Map.of();
        updater.loadState(updaterState);

        Loggers.AGENT.info("[resume] epoch={} best={}", progress.getStartEpoch(), progress.getBestScore());
    }

    private void saveCheckpointIfNeeded(Object agent, Progress progress, boolean improved) {
        if (checkpointStore == null || checkpointManager == null) {
            return;
        }
        if (!checkpointManager.shouldSave(progress.getCurrentEpoch(), improved)) {
            return;
        }
        EvolveCheckpoint checkpoint = checkpointManager.buildCheckpoint(agent, progress, updater.getState());
        String path = checkpointStore.saveCheckpoint(checkpoint, "latest.json");
        Loggers.AGENT.info("[checkpoint] saved: {}", path);
    }

    private EvaluationResult selectBestCandidateOnVal(
            Object agent,
            Map<String, Object> operators,
            List<Updates> candidates,
            CaseLoader valCases
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return evaluate(agent, valCases);
        }

        Map<String, Map<String, Object>> baseState = snapshotOperatorsState(operators);
        double bestScore = Double.NEGATIVE_INFINITY;
        List<EvaluatedCase> bestEvaluated = List.of();
        Map<String, Map<String, Object>> bestState = null;

        for (Updates candidate : candidates) {
            restoreOperatorsState(operators, baseState);
            applyUpdates(operators, candidate != null ? candidate : new Updates());

            EvaluationResult candidateResult = evaluate(agent, valCases);
            if (candidateResult.score() > bestScore) {
                bestScore = candidateResult.score();
                bestEvaluated = candidateResult.evaluatedCases();
                bestState = snapshotOperatorsState(operators);
            }
        }

        if (bestState != null) {
            restoreOperatorsState(operators, bestState);
            return new EvaluationResult(bestScore, bestEvaluated);
        }

        restoreOperatorsState(operators, baseState);
        return evaluate(agent, valCases);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOperatorRegistry(Object agent) {
        Object value = invokeGetter(agent, "getOperators");
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        value = invokeGetter(agent, "get_operators");
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Map<String, Object>> snapshotOperatorsState(Map<String, Object> operators) {
        Map<String, Map<String, Object>> state = new LinkedHashMap<>();
        if (operators == null) {
            return state;
        }
        for (Map.Entry<String, Object> entry : operators.entrySet()) {
            Object operator = entry.getValue();
            Object rawState = invokeGetter(operator, "getState");
            if (rawState instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedState = new LinkedHashMap<>((Map<String, Object>) map);
                state.put(entry.getKey(), typedState);
            }
        }
        return state;
    }

    private void restoreOperatorsState(Map<String, Object> operators, Map<String, Map<String, Object>> state) {
        if (operators == null || state == null) {
            return;
        }
        for (Map.Entry<String, Map<String, Object>> entry : state.entrySet()) {
            Object operator = operators.get(entry.getKey());
            if (operator == null) {
                continue;
            }
            invokeIfPresent(operator, "loadState", new Class<?>[]{Map.class}, entry.getValue());
        }
    }

    private List<Updates> coerceCandidates(Object updated) {
        if (!(updated instanceof List<?> rawCandidates)) {
            return null;
        }
        List<Updates> candidates = new ArrayList<>(rawCandidates.size());
        for (Object item : rawCandidates) {
            if (item instanceof Updates updates) {
                candidates.add(updates);
                continue;
            }
            if (item instanceof Map<?, ?> map) {
                Updates updates = new Updates();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof UpdateKey key) {
                        updates.put(key, entry.getValue());
                    }
                }
                candidates.add(updates);
            }
        }
        return candidates;
    }

    private Callable<PredictionAndSession> buildPredictionTask(Object agent, Case caseData) {
        return () -> {
            Object session = createSession();
            Map<String, Object> prediction = invokeAgent(agent, caseData, session);
            return new PredictionAndSession(prediction, session);
        };
    }

    private Object createSession() {
        return new AgentSession(UUID.randomUUID().toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeAgent(Object agent, Case caseData, Object session) {
        Map<String, Object> inputs = new LinkedHashMap<>(caseData.getInputs());
        inputs.put("conversation_id", caseData.getCaseId());
        try {
            Object result;
            if (agent instanceof com.openjiuwen.core.singleagent.BaseAgent baseAgent && session instanceof Session typedSession) {
                result = baseAgent.invoke(inputs, typedSession);
            } else {
                Method method = agent.getClass().getMethod("invoke", Object.class, Session.class);
                result = method.invoke(agent, inputs, session);
            }
            if (result instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
            return new LinkedHashMap<>(Map.of("output", result));
        } catch (Exception e) {
            return new LinkedHashMap<>(Map.of("error", "Get wrong result due to " + rootMessage(e)));
        }
    }

    private Object invokeGetter(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void invokeIfPresent(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.invoke(target, args);
        } catch (Exception ignored) {
        }
    }

    private int intValue(Map<String, Object> values, String key, int defaultValue) {
        if (values == null || !values.containsKey(key)) {
            return defaultValue;
        }
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private double doubleValue(Map<String, Object> values, String key, double defaultValue) {
        if (values == null || !values.containsKey(key)) {
            return defaultValue;
        }
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : exception.getMessage();
    }

    private record PredictionAndSession(Map<String, Object> prediction, Object session) {
    }

    /**
     * Builder for Trainer.
     */
    public static class Builder {
        private Updater updater;
        private BaseEvaluator evaluator;
        private TracerTrajectoryExtractor extractor;
        private Callbacks callbacks;
        private int numParallel = TuneConstant.DEFAULT_PARALLEL_NUM;
        private double earlyStopScore = TuneConstant.DEFAULT_EARLY_STOP_SCORE;
        private String checkpointDir;
        private String resumeFrom;
        private int checkpointEveryNEpochs = 1;
        private boolean checkpointOnImprove = true;

        public Builder updater(Updater updater) {
            this.updater = updater;
            return this;
        }

        public Builder evaluator(BaseEvaluator evaluator) {
            this.evaluator = evaluator;
            return this;
        }

        public Builder extractor(TracerTrajectoryExtractor extractor) {
            this.extractor = extractor;
            return this;
        }

        public Builder callbacks(Callbacks callbacks) {
            this.callbacks = callbacks;
            return this;
        }

        public Builder numParallel(int numParallel) {
            this.numParallel = numParallel;
            return this;
        }

        public Builder earlyStopScore(double earlyStopScore) {
            this.earlyStopScore = earlyStopScore;
            return this;
        }

        public Builder checkpointDir(String checkpointDir) {
            this.checkpointDir = checkpointDir;
            return this;
        }

        public Builder resumeFrom(String resumeFrom) {
            this.resumeFrom = resumeFrom;
            return this;
        }

        public Builder checkpointEveryNEpochs(int checkpointEveryNEpochs) {
            this.checkpointEveryNEpochs = checkpointEveryNEpochs;
            return this;
        }

        public Builder checkpointOnImprove(boolean checkpointOnImprove) {
            this.checkpointOnImprove = checkpointOnImprove;
            return this;
        }

        public Trainer build() {
            if (updater == null) {
                throw new IllegalStateException("updater is required");
            }
            if (evaluator == null) {
                throw new IllegalStateException("evaluator is required");
            }
            TuneUtils.validateDigitalParameter(
                    numParallel,
                    "num_parallel",
                    TuneConstant.MIN_PARALLEL_NUM,
                    TuneConstant.MAX_PARALLEL_NUM
            );
            TuneUtils.validateDigitalParameter(earlyStopScore, "early_stop_score", 0.0, 1.0);
            return new Trainer(this);
        }
    }
}
