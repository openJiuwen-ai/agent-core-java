/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trainer;

import com.openjiuwen.agent_evolving.TuneConstant;
import com.openjiuwen.agent_evolving.TuneUtils;
import com.openjiuwen.agent_evolving.checkpointing.CheckpointManager;
import com.openjiuwen.agent_evolving.checkpointing.DefaultCheckpointManager;
import com.openjiuwen.agent_evolving.checkpointing.EvolveCheckpoint;
import com.openjiuwen.agent_evolving.checkpointing.FileCheckpointStore;
import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.CaseLoader;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.evaluator.BaseEvaluator;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TracerTrajectoryExtractor;
import com.openjiuwen.agent_evolving.trajectory.UpdateKey;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.agent_evolving.updater.Updater;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.BaseAgent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * Orchestrates "evaluate -> update -> writeback" self-evolution cycle.
 *
 * <p>Accepts {@link Updater} and {@link BaseEvaluator}, manages checkpoint/resume.</p>
 *
 * <p>Mirrors Python's {@code Trainer} in
 * {@code openjiuwen/agent_evolving/trainer/trainer.py}.</p>
 */
public class Trainer {

    private static final Logger LOGGER = Logger.getLogger(Trainer.class.getName());

    private final Updater updater;
    private final BaseEvaluator evaluator;
    private final TracerTrajectoryExtractor extractor;
    private Callbacks callbacks;
    private final int numParallel;
    private final double earlyStopScore;
    private final FileCheckpointStore checkpointStore;
    private final String resumeFrom;
    private final CheckpointManager checkpointManager;

    public Trainer(Builder builder) {
        this.updater = Objects.requireNonNull(builder.updater, "updater");
        this.evaluator = Objects.requireNonNull(builder.evaluator, "evaluator");
        this.extractor = builder.extractor != null ? builder.extractor : new TracerTrajectoryExtractor();
        this.callbacks = builder.callbacks != null ? builder.callbacks : new Callbacks();
        this.numParallel = builder.numParallel;
        this.earlyStopScore = builder.earlyStopScore;
        this.checkpointStore = builder.checkpointDir != null ? new FileCheckpointStore(builder.checkpointDir) : null;
        this.resumeFrom = builder.resumeFrom;
        this.checkpointManager = builder.checkpointManager != null
                ? builder.checkpointManager
                : checkpointStore == null
                        ? null
                        : new DefaultCheckpointManager(
                                null,
                                "v1",
                                builder.checkpointEveryNEpochs,
                                builder.checkpointOnImprove
                        );
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Set training lifecycle callbacks.
     *
     * @param callbacks lifecycle callbacks
     */
    public void setCallbacks(Callbacks callbacks) {
        this.callbacks = callbacks != null ? callbacks : new Callbacks();
    }

    /**
     * Execute self-evolving training.
     *
     * @param agent agent to optimize
     * @param trainCases training cases
     * @param valCases validation cases; uses train cases when null
     * @param numIterations maximum training epochs
     * @param kwargs updater configuration
     * @return trained agent
     */
    public BaseAgent train(
            BaseAgent agent,
            CaseLoader trainCases,
            CaseLoader valCases,
            int numIterations,
            Map<String, Object> kwargs) {
        Objects.requireNonNull(agent, "agent");
        Progress progress = new Progress();
        progress.setMaxEpoch(numIterations);
        CaseLoader effectiveValCases = valCases != null ? valCases : trainCases;
        Map<String, Object> config = kwargs != null ? new LinkedHashMap<>(kwargs) : new LinkedHashMap<>();

        Map<String, Operator> operators = getOperatorRegistry(agent);
        if (bindUpdater(operators, config) == 0) {
            LOGGER.severe("[Trainer] no operator matches updater targets; soft-exit without training.");
            return agent;
        }

        resumeIfNeeded(agent, progress);

        List<EvaluatedCase> currentEvaluated = List.of();
        if (updaterRequiresForward()) {
            EvaluationResult baseline = evaluate(agent, effectiveValCases);
            progress.setCurrentEpochScore(baseline.score());
            progress.setBestScore(Math.max(progress.getBestScore(), baseline.score()));
            currentEvaluated = baseline.evaluatedCases();
        } else {
            progress.setCurrentEpochScore(0.0d);
        }

        callbacks.onTrainBegin(agent, progress, currentEvaluated);
        if (progress.getBestScore() >= earlyStopScore) {
            callbacks.onTrainEnd(agent, progress, currentEvaluated);
            return agent;
        }

        for (int ignored : progress.runEpoch()) {
            callbacks.onTrainEpochBegin(agent, progress);

            ForwardResult forwardResult;
            if (updaterRequiresForward()) {
                forwardResult = forward(agent, trainCases);
                progress.setCurrentEpochScore(forwardResult.score());
            } else {
                forwardResult = new ForwardResult(0.0d, List.of(), List.of(), List.of());
                progress.setCurrentEpochScore(0.0d);
            }

            Object updated = updater.update(
                    forwardResult.trajectories(),
                    new ArrayList<>(forwardResult.evaluatedCases()),
                    config
            ).toCompletableFuture().join();

            EvaluationResult validationResult;
            List<Updates> candidates = coerceCandidates(updated);
            if (candidates != null) {
                validationResult = selectBestCandidateOnVal(agent, operators, candidates, effectiveValCases);
            } else {
                applyUpdates(operators, coerceUpdates(updated));
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

        callbacks.onTrainEnd(agent, progress, currentEvaluated);
        return agent;
    }

    public BaseAgent train(BaseAgent agent, CaseLoader trainCases, CaseLoader valCases, int numIterations) {
        return train(agent, trainCases, valCases, numIterations, Map.of());
    }

    /**
     * Single forward pass on cases: inference, evaluation, trajectory extraction.
     *
     * @param agent agent under evaluation
     * @param cases case loader
     * @return forward result
     */
    public ForwardResult forward(BaseAgent agent, CaseLoader cases) {
        if (cases == null || cases.size() == 0) {
            return new ForwardResult(0.0d, List.of(), List.of(), List.of());
        }
        PredictionResult predictionResult = predict(agent, cases);
        List<Case> caseList = cases.getCases();
        List<EvaluatedCase> evaluated = evaluator.batchEvaluate(caseList, predictionResult.predictions(), numParallel);
        double score = meanScore(evaluated);

        List<Trajectory> trajectories = new ArrayList<>(caseList.size());
        for (int index = 0; index < caseList.size(); index++) {
            trajectories.add(extractor.extract(predictionResult.sessions().get(index), caseList.get(index).getCaseId()));
        }
        return new ForwardResult(score, evaluated, trajectories, predictionResult.sessions());
    }

    /**
     * Run inference and evaluation on cases.
     */
    public EvaluationResult evaluate(BaseAgent agent, CaseLoader cases) {
        if (cases == null || cases.size() == 0) {
            return new EvaluationResult(0.0d, List.of());
        }
        List<Map<String, Object>> predictions = predictOnly(agent, cases);
        List<EvaluatedCase> evaluated = evaluator.batchEvaluate(cases.getCases(), predictions, numParallel);
        return new EvaluationResult(meanScore(evaluated), evaluated);
    }

    /**
     * Run inference only, return model outputs per case.
     */
    public List<Map<String, Object>> predictOnly(BaseAgent agent, CaseLoader cases) {
        return predict(agent, cases).predictions();
    }

    /**
     * Run agent.invoke on each case with a fresh session, bounded by numParallel.
     */
    public PredictionResult predict(BaseAgent agent, CaseLoader cases) {
        if (cases == null || cases.size() == 0) {
            return new PredictionResult(List.of(), List.of());
        }
        List<Case> caseList = cases.getCases();
        int workers = Math.max(1, Math.min(numParallel, caseList.size()));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<PredictionAndSession>> futures = new ArrayList<>(caseList.size());
            for (Case caseValue : caseList) {
                futures.add(executor.submit(buildPredictionTask(agent, caseValue)));
            }

            List<Map<String, Object>> predictions = new ArrayList<>(caseList.size());
            List<AgentSessionApi> sessions = new ArrayList<>(caseList.size());
            for (Future<PredictionAndSession> future : futures) {
                PredictionAndSession item = futureResult(future);
                predictions.add(item.prediction());
                sessions.add(item.session());
            }
            return new PredictionResult(predictions, sessions);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Apply updater-generated updates to operator registry.
     */
    public static void applyUpdates(Map<String, Operator> operators, Updates updates) {
        if (operators == null || operators.isEmpty() || updates == null || updates.isEmpty()) {
            return;
        }
        for (Map.Entry<UpdateKey, Object> entry : updates.entrySet()) {
            UpdateKey key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            Operator operator = operators.get(key.getOperatorId());
            if (operator != null) {
                operator.setParameter(key.getTarget(), value);
            }
        }
    }

    public static Map<String, Map<String, Object>> snapshotOperatorsState(Map<String, Operator> operators) {
        Map<String, Map<String, Object>> state = new LinkedHashMap<>();
        if (operators == null) {
            return state;
        }
        for (Map.Entry<String, Operator> entry : operators.entrySet()) {
            state.put(entry.getKey(), new LinkedHashMap<>(entry.getValue().getState()));
        }
        return state;
    }

    public static void restoreOperatorsState(
            Map<String, Operator> operators,
            Map<String, Map<String, Object>> state) {
        if (operators == null || state == null) {
            return;
        }
        for (Map.Entry<String, Map<String, Object>> entry : state.entrySet()) {
            Operator operator = operators.get(entry.getKey());
            if (operator != null) {
                operator.loadState(entry.getValue());
            }
        }
    }

    private double meanScore(List<EvaluatedCase> evaluated) {
        if (evaluated == null || evaluated.isEmpty()) {
            return 0.0d;
        }
        return evaluated.stream().mapToDouble(EvaluatedCase::getScore).average().orElse(0.0d);
    }

    private int bindUpdater(Map<String, Operator> operators, Map<String, Object> config) {
        return updater.bind(operators, null, config);
    }

    private boolean updaterRequiresForward() {
        return updater.requiresForwardData();
    }

    private void resumeIfNeeded(BaseAgent agent, Progress progress) {
        if (checkpointStore == null || checkpointManager == null || resumeFrom == null || resumeFrom.isBlank()) {
            return;
        }
        EvolveCheckpoint checkpoint = checkpointStore.loadCheckpoint(resumeFrom);
        if (checkpoint == null) {
            return;
        }
        Map<String, Object> restored = checkpointManager.restore(agent, checkpoint);
        progress.setStartEpoch(intValue(restored.get("start_epoch"), 0));
        progress.setBestScore(doubleValue(restored.get("best_score"), 0.0d));
        updater.loadState(checkpoint.getUpdaterState() == null ? Map.of() : checkpoint.getUpdaterState());
        LOGGER.info(() -> "[resume] epoch=" + progress.getStartEpoch() + " best=" + progress.getBestScore());
    }

    private void saveCheckpointIfNeeded(BaseAgent agent, Progress progress, boolean improved) {
        if (checkpointStore == null || checkpointManager == null) {
            return;
        }
        if (!checkpointManager.shouldSave(progress.getCurrentEpoch(), improved)) {
            return;
        }
        EvolveCheckpoint checkpoint = checkpointManager.buildCheckpoint(agent, progress, updater.getState());
        String path = checkpointStore.saveCheckpoint(checkpoint, "latest.json");
        LOGGER.info(() -> "[checkpoint] saved: " + path);
    }

    private EvaluationResult selectBestCandidateOnVal(
            BaseAgent agent,
            Map<String, Operator> operators,
            List<Updates> candidates,
            CaseLoader valCases) {
        if (candidates == null || candidates.isEmpty()) {
            return evaluate(agent, valCases);
        }
        Map<String, Map<String, Object>> baseState = snapshotOperatorsState(operators);
        double bestScore = Double.NEGATIVE_INFINITY;
        List<EvaluatedCase> bestEvaluated = List.of();
        Map<String, Map<String, Object>> bestState = null;

        for (int index = 0; index < candidates.size(); index++) {
            Updates candidate = candidates.get(index);
            restoreOperatorsState(operators, baseState);
            applyUpdates(operators, candidate == null ? new Updates() : candidate);
            EvaluationResult candidateResult = evaluate(agent, valCases);
            int candidateIndex = index;
            LOGGER.info(() -> String.format("[candidate] idx=%d val_score=%.4f", candidateIndex, candidateResult.score()));
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
    private Map<String, Operator> getOperatorRegistry(BaseAgent agent) {
        Object value = invokeNoArgs(agent, "get_operators", "getOperators");
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Operator> operators = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getValue() instanceof Operator operator) {
                operators.put(String.valueOf(entry.getKey()), operator);
            }
        }
        return operators;
    }

    private List<Updates> coerceCandidates(Object updated) {
        if (!(updated instanceof List<?> rawCandidates)) {
            return null;
        }
        List<Updates> candidates = new ArrayList<>(rawCandidates.size());
        for (Object candidate : rawCandidates) {
            candidates.add(coerceUpdates(candidate));
        }
        return candidates;
    }

    private Updates coerceUpdates(Object updated) {
        if (updated == null) {
            return new Updates();
        }
        if (updated instanceof Updates updates) {
            return updates;
        }
        Updates updates = new Updates();
        if (updated instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof UpdateKey key) {
                    updates.put(key, entry.getValue());
                }
            }
        }
        return updates;
    }

    private Callable<PredictionAndSession> buildPredictionTask(BaseAgent agent, Case caseValue) {
        return () -> {
            AgentSessionApi session = createAgentSession(agent);
            Map<String, Object> inputs = new LinkedHashMap<>(caseValue.getInputs());
            inputs.put("conversation_id", caseValue.getCaseId());
            try {
                Object result = agent.invoke(inputs, session).toCompletableFuture().join();
                return new PredictionAndSession(toPredictionMap(result), session);
            } catch (RuntimeException exception) {
                return new PredictionAndSession(
                        new LinkedHashMap<>(Map.of("error", "Get wrong result due to " + rootMessage(exception))),
                        session
                );
            }
        };
    }

    private AgentSessionApi createAgentSession(BaseAgent agent) {
        Object card = agent == null ? null : agent.getCard();
        return new AgentSession(UUID.randomUUID().toString(), null, card);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toPredictionMap(Object result) {
        if (result instanceof Map<?, ?> rawMap) {
            Map<String, Object> output = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                output.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return output;
        }
        return new LinkedHashMap<>(Map.of("output", result));
    }

    private Object invokeNoArgs(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            Class<?> current = target.getClass();
            while (current != null) {
                try {
                    Method method = current.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (NoSuchMethodException exception) {
                    current = current.getSuperclass();
                } catch (ReflectiveOperationException exception) {
                    return null;
                }
            }
        }
        return null;
    }

    private static PredictionAndSession futureResult(Future<PredictionAndSession> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("prediction interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("prediction failed", cause);
        }
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }

    public Updater getUpdater() {
        return updater;
    }

    public BaseEvaluator getEvaluator() {
        return evaluator;
    }

    public TracerTrajectoryExtractor getExtractor() {
        return extractor;
    }

    public Callbacks getCallbacks() {
        return callbacks;
    }

    public int getNumParallel() {
        return numParallel;
    }

    public double getEarlyStopScore() {
        return earlyStopScore;
    }

    public FileCheckpointStore getCheckpointStore() {
        return checkpointStore;
    }

    public String getResumeFrom() {
        return resumeFrom;
    }

    public CheckpointManager getCheckpointManager() {
        return checkpointManager;
    }

    public record ForwardResult(
            double score,
            List<EvaluatedCase> evaluatedCases,
            List<Trajectory> trajectories,
            List<AgentSessionApi> sessions) {
        public ForwardResult {
            evaluatedCases = List.copyOf(evaluatedCases);
            trajectories = List.copyOf(trajectories);
            sessions = List.copyOf(sessions);
        }
    }

    public record EvaluationResult(double score, List<EvaluatedCase> evaluatedCases) {
        public EvaluationResult {
            evaluatedCases = List.copyOf(evaluatedCases);
        }
    }

    public record PredictionResult(List<Map<String, Object>> predictions, List<AgentSessionApi> sessions) {
        public PredictionResult {
            predictions = List.copyOf(predictions);
            sessions = List.copyOf(sessions);
        }
    }

    private record PredictionAndSession(Map<String, Object> prediction, AgentSessionApi session) {
    }

    /**
     * Builder for {@link Trainer}.
     */
    public static final class Builder {
        private Updater updater;
        private BaseEvaluator evaluator;
        private TracerTrajectoryExtractor extractor;
        private Callbacks callbacks;
        private int numParallel = TuneConstant.defaultParallelNum;
        private double earlyStopScore = TuneConstant.defaultEarlyStopScore;
        private String checkpointDir;
        private String resumeFrom;
        private int checkpointEveryNEpochs = 1;
        private boolean checkpointOnImprove = true;
        private CheckpointManager checkpointManager;

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

        public Builder checkpointManager(CheckpointManager checkpointManager) {
            this.checkpointManager = checkpointManager;
            return this;
        }

        public Trainer build() {
            TuneUtils.validateDigitalParameter(
                    numParallel,
                    "num_parallel",
                    TuneConstant.minParallelNum,
                    TuneConstant.maxParallelNum
            );
            TuneUtils.validateDigitalParameter(earlyStopScore, "early_stop_score", 0.0d, 1.0d);
            return new Trainer(this);
        }
    }
}
