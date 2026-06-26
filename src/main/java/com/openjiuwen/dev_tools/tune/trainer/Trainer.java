/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.trainer;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.core.singleagent.legacy.LegacyBaseAgent;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneConstant;
import com.openjiuwen.dev_tools.tune.TuneUtils;
import com.openjiuwen.dev_tools.tune.dataset.CaseLoader;
import com.openjiuwen.dev_tools.tune.evaluator.BaseEvaluator;
import com.openjiuwen.dev_tools.tune.optimizer.BaseOptimizer;
import com.openjiuwen.dev_tools.tune.optimizer.TextualParameter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Prompt optimization trainer.
 *
 * <p>Mirrors Python's {@code Trainer} and module constant in
 * {@code openjiuwen/dev_tools/tune/trainer/trainer.py}.</p>
 */
public class Trainer {

    public static final int DEFAULT_CANDIDATES_SAMPLE_NUM = 6;

    private final BaseOptimizer optimizer;
    private final BaseEvaluator evaluator;
    private final int numParallel;
    private final double earlyStopScore;
    private Callbacks callbacks = new Callbacks();

    public Trainer(BaseOptimizer optimizer, BaseEvaluator evaluator) {
        this(optimizer, evaluator, Map.of());
    }

    public Trainer(BaseOptimizer optimizer, BaseEvaluator evaluator, Map<String, Object> kwargs) {
        this.optimizer = Objects.requireNonNull(optimizer, "optimizer");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        Map<String, Object> options = kwargs == null ? Map.of() : kwargs;
        this.numParallel = intOption(options, "num_parallel", TuneConstant.DEFAULT_PARALLEL_NUM);
        TuneUtils.validateDigitalParameter(
                numParallel,
                "num_parallel",
                TuneConstant.MIN_PARALLEL_NUM,
                TuneConstant.MAX_PARALLEL_NUM
        );
        this.earlyStopScore = doubleOption(options, "early_stop_score", TuneConstant.DEFAULT_EARLY_STOP_SCORE);
        TuneUtils.validateDigitalParameter(earlyStopScore, "early_stop_score", 0.0d, 1.0d);
    }

    public LegacyBaseAgent train(LegacyBaseAgent agent, CaseLoader trainCases) {
        return train(agent, trainCases, null, Map.of());
    }

    public LegacyBaseAgent train(LegacyBaseAgent agent, CaseLoader trainCases, CaseLoader valCases) {
        return train(agent, trainCases, valCases, Map.of());
    }

    public LegacyBaseAgent train(LegacyBaseAgent agent,
                                 CaseLoader trainCases,
                                 CaseLoader valCases,
                                 Map<String, Object> kwargs) {
        if (!checkTrainable(agent)) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_TRAINER_EXECUTION_ERROR,
                    "error_msg",
                    "trainer only support current Agent right now"
            );
        }
        Progress progress = preTrain(agent, kwargs);
        CaseLoader validationCases = valCases == null ? trainCases : valCases;
        EvalResult baseline = evaluate(agent, validationCases);
        progress.setCurrentEpochScore(baseline.score());
        progress.setBestScore(progress.getCurrentEpochScore());
        List<EvaluatedCase> curEpochEvaluatedCases = baseline.evaluatedCases();
        callbacks.onTrainBegin(agent, progress, curEpochEvaluatedCases);
        if (progress.getBestScore() >= earlyStopScore) {
            Loggers.AGENT.info("val set score {} already exceed target score {}, skip optimization",
                    progress.getBestScore(), earlyStopScore);
            callbacks.onTrainEnd(agent, progress, curEpochEvaluatedCases);
            return agent;
        }
        Loggers.AGENT.info("val set baseline score: {}", progress.getCurrentEpochScore());
        ParameterSearcher parameterSearcher = new ParameterSearcher(this, validationCases);
        double score = 0.0d;
        for (Integer ignoredEpoch : progress.runEpoch()) {
            callbacks.onTrainEpochBegin(agent, progress);
            EvalResult trainResult;
            try (BaseOptimizer ignored = optimizer.enter()) {
                trainResult = evaluate(agent, trainCases);
                Loggers.AGENT.info("train epoch {}, train set score: {}",
                        progress.getCurrentEpoch(), trainResult.score());
            }
            List<EvaluatedCase> curEvaluatedCases = trainResult.evaluatedCases();
            Map<String, ?> curParameters = snapshotLlmCalls(agent);
            Map<String, ?> bestBatchParameters = curParameters;
            for (Integer ignoredBatch : progress.runBatch()) {
                try (BaseOptimizer ignored = optimizer.enter()) {
                    optimizer.backward(curEvaluatedCases);
                    optimizer.update();
                }
                ParameterSearcher.SearchResult searchResult = parameterSearcher.searchBest(
                        agent,
                        progress.getBestScore(),
                        curParameters,
                        List.of(snapshotLlmCalls(agent))
                );
                score = searchResult.score();
                curEpochEvaluatedCases = searchResult.evaluatedCases();
                progress.setCurrentEpochScore(searchResult.lastScore());
                if (score > progress.getBestBatchScore()) {
                    progress.setBestBatchScore(score);
                    bestBatchParameters = searchResult.parameters();
                }
            }
            Loggers.AGENT.info("train epoch {}, val set score: {}", progress.getCurrentEpoch(), score);
            if (progress.getBestBatchScore() > progress.getBestScore()) {
                progress.setBestScore(progress.getBestBatchScore());
                updateAgent(agent, bestBatchParameters);
                callbacks.onTrainEpochEnd(agent, progress, curEpochEvaluatedCases);
            } else {
                callbacks.onTrainEpochEnd(agent, progress, curEpochEvaluatedCases);
                updateAgent(agent, curParameters);
            }
            if (progress.getBestScore() >= earlyStopScore) {
                break;
            }
        }
        callbacks.onTrainEnd(agent, progress, curEpochEvaluatedCases);
        return agent;
    }

    public EvalResult evaluate(LegacyBaseAgent agent, CaseLoader cases) {
        List<Case> caseList = cases.getCases();
        if (caseList.isEmpty()) {
            return new EvalResult(0.0d, List.of());
        }
        List<Map<String, Object>> predicts = predict(agent, cases);
        List<EvaluatedCase> evaluatedCases = evaluator.batchEvaluate(caseList, predicts, numParallel);
        double score = evaluatedCases.isEmpty()
                ? 0.0d
                : evaluatedCases.stream().mapToDouble(EvaluatedCase::getScore).sum() / evaluatedCases.size();
        return new EvalResult(score, evaluatedCases);
    }

    public List<Map<String, Object>> predict(LegacyBaseAgent agent, CaseLoader cases) {
        if (cases.size() == 0) {
            return List.of();
        }
        int numWorkers = Math.min(numParallel, cases.size());
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>(cases.size());
            for (Case caseValue : cases.getCases()) {
                futures.add(executor.submit(() -> forward(agent, caseValue)));
            }
            List<Map<String, Object>> predicts = new ArrayList<>(futures.size());
            for (Future<Map<String, Object>> future : futures) {
                predicts.add(futureResult(future));
            }
            return predicts;
        } finally {
            executor.shutdown();
        }
    }

    public void setCallbacks(Callbacks callbacks) {
        if (callbacks == null) {
            Loggers.AGENT.warn("callbacks should be a Callbacks object, got null");
            return;
        }
        this.callbacks = callbacks;
    }

    public void set_callbacks(Callbacks callbacks) {
        setCallbacks(callbacks);
    }

    public void updateAgent(LegacyBaseAgent agent, Map<String, ?> parameters) {
        updateAgentParameters(agent, parameters);
    }

    public void update_agent(LegacyBaseAgent agent, Map<String, ?> parameters) {
        updateAgent(agent, parameters);
    }

    public int getNumParallel() {
        return numParallel;
    }

    public double getEarlyStopScore() {
        return earlyStopScore;
    }

    private Progress preTrain(LegacyBaseAgent agent, Map<String, Object> kwargs) {
        Map<String, Object> options = kwargs == null ? Map.of() : kwargs;
        int maxEpoch = intOption(options, "num_iterations", TuneConstant.DEFAULT_ITERATION_NUM);
        TuneUtils.validateDigitalParameter(
                maxEpoch,
                "num_iterations",
                TuneConstant.MIN_ITERATION_NUM,
                TuneConstant.MAX_ITERATION_NUM
        );
        Progress progress = new Progress();
        progress.setMaxEpoch(maxEpoch);
        optimizer.bindParameter(getLlmCalls(agent));
        return progress;
    }

    private static Map<String, Object> forward(LegacyBaseAgent agent, Case caseValue) {
        Map<String, Object> inputs = new LinkedHashMap<>(caseValue.getInputs());
        inputs.put("conversation_id", caseValue.getCaseId());
        try {
            Object result = agent.invoke(inputs, null).toCompletableFuture().get();
            return normalizePredictResult(result);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Map.of("error", "Get wrong result due to " + errorMessage(exception));
        } catch (ExecutionException | RuntimeException exception) {
            return Map.of("error", "Get wrong result due to " + errorMessage(exception));
        }
    }

    private static Map<String, Object> normalizePredictResult(Object result) {
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        return Map.of("output", result);
    }

    private static Map<String, Object> futureResult(Future<Map<String, Object>> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Map.of("error", "Get wrong result due to " + errorMessage(exception));
        } catch (ExecutionException exception) {
            return Map.of("error", "Get wrong result due to " + errorMessage(exception));
        }
    }

    private static String errorMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while ((cursor instanceof CompletionException || cursor instanceof ExecutionException)
                && cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null ? cursor.getClass().getSimpleName() : message;
    }

    private static boolean checkTrainable(LegacyBaseAgent agent) {
        Method method = getNoArgMethod(agent.getClass(), "get_llm_calls");
        if (method == null) {
            return false;
        }
        Class<?> superclass = agent.getClass().getSuperclass();
        return superclass == null || !method.getDeclaringClass().equals(superclass);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, LLMCall> getLlmCalls(LegacyBaseAgent agent) {
        Method method = getNoArgMethod(agent.getClass(), "get_llm_calls");
        if (method == null) {
            method = getNoArgMethod(agent.getClass(), "getLlmCalls");
        }
        if (method == null) {
            throw new IllegalStateException("Agent does not expose get_llm_calls()");
        }
        try {
            Object value = method.invoke(agent);
            if (!(value instanceof Map<?, ?> rawMap)) {
                throw new IllegalStateException("get_llm_calls() must return a map");
            }
            Map<String, LLMCall> result = new LinkedHashMap<>();
            rawMap.forEach((key, item) -> result.put(String.valueOf(key), (LLMCall) item));
            return result;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("get_llm_calls() is not accessible", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("get_llm_calls() failed", cause);
        } catch (ClassCastException exception) {
            throw new IllegalStateException("get_llm_calls() must return LLMCall values", exception);
        }
    }

    private static Method getNoArgMethod(Class<?> type, String methodName) {
        try {
            return type.getMethod(methodName);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    static Map<String, ?> snapshotLlmCalls(LegacyBaseAgent agent) {
        return snapshotParameterMap(getLlmCalls(agent));
    }

    static Map<String, ?> snapshotParameterMap(Map<String, ?> parameters) {
        Map<String, PromptSnapshot> snapshots = new LinkedHashMap<>();
        if (parameters == null) {
            return snapshots;
        }
        for (Map.Entry<String, ?> entry : parameters.entrySet()) {
            PromptSnapshot snapshot = PromptSnapshot.from(entry.getValue());
            if (snapshot != null) {
                snapshots.put(entry.getKey(), snapshot);
            }
        }
        return snapshots;
    }

    private static void updateAgentParameters(LegacyBaseAgent agent, Map<String, ?> parameters) {
        if (parameters == null) {
            return;
        }
        Map<String, LLMCall> agentParameters = getLlmCalls(agent);
        for (Map.Entry<String, LLMCall> entry : agentParameters.entrySet()) {
            Object param = parameters.get(entry.getKey());
            if (param == null) {
                continue;
            }
            PromptSnapshot snapshot = PromptSnapshot.from(param);
            if (snapshot == null) {
                continue;
            }
            entry.getValue().updateSystemPrompt(snapshot.systemPrompt());
            entry.getValue().updateUserPrompt(snapshot.userPrompt());
        }
    }

    private static int intOption(Map<String, Object> options, String name, int defaultValue) {
        Object value = options.getOrDefault(name, defaultValue);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double doubleOption(Map<String, Object> options, String name, double defaultValue) {
        Object value = options.getOrDefault(name, defaultValue);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    /**
     * Mirrors Python's {@code Trainer.evaluate} tuple return in
     * {@code openjiuwen/dev_tools/tune/trainer/trainer.py}.
     */
    public record EvalResult(double score, List<EvaluatedCase> evaluatedCases) {
    }

    /**
     * Mirrors Python's {@code copy.deepcopy(...)} parameter snapshots in
     * {@code openjiuwen/dev_tools/tune/trainer/trainer.py}.
     */
    static record PromptSnapshot(String systemPrompt, String userPrompt) {
        static PromptSnapshot from(Object value) {
            if (value instanceof PromptSnapshot snapshot) {
                return snapshot;
            }
            if (value instanceof TextualParameter textualParameter) {
                return from(textualParameter.getLlmCall());
            }
            if (value instanceof LLMCall llmCall) {
                return new PromptSnapshot(
                        TuneUtils.getContentStringFromTemplate(llmCall.getSystemPrompt()),
                        TuneUtils.getContentStringFromTemplate(llmCall.getUserPrompt())
                );
            }
            return null;
        }
    }
}
