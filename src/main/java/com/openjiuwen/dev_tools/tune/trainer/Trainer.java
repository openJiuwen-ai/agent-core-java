/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.dev_tools.tune.trainer;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.core.singleagent.legacy.BaseAgent;
import com.openjiuwen.dev_tools.tune.*;
import com.openjiuwen.dev_tools.tune.dataset.CaseLoader;
import com.openjiuwen.dev_tools.tune.evaluator.BaseEvaluator;
import com.openjiuwen.dev_tools.tune.evaluator.DefaultEvaluator;
import com.openjiuwen.dev_tools.tune.optimizer.BaseOptimizer;
import com.openjiuwen.dev_tools.tune.optimizer.JointOptimizer;
import com.openjiuwen.dev_tools.tune.optimizer.TextualParameter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Trainer for prompt optimization.
 *
 * <p>Mirrors Python's {@code Trainer} in {@code openjiuwen.dev_tools.tune.trainer.trainer}.
 */
public class Trainer {

    private static final int DEFAULT_CANDIDATES_SAMPLE_NUM = 6;

    private final BaseOptimizer optimizer;
    private final BaseEvaluator evaluator;
    private final int numParallel;
    private final double earlyStopScore;
    private Callbacks callbacks;

    /**
     * Creates a Trainer.
     *
     * @param optimizer the optimizer
     * @param evaluator the evaluator
     * @param kwargs additional options (num_parallel, early_stop_score)
     */
    public Trainer(DefaultEvaluator evaluator, JointOptimizer optimizer, int numParallel, double earlyStopScore) {
        validateLegacyRange(numParallel, "num_parallel", TuneConstant.MIN_PARALLEL_NUM, TuneConstant.MAX_PARALLEL_NUM);
        validateLegacyRange(earlyStopScore, "early_stop_score", 0.0, 1.0);
        this.optimizer = optimizer;
        this.evaluator = evaluator;
        this.numParallel = numParallel;
        this.earlyStopScore = earlyStopScore;
        this.callbacks = new Callbacks();
    }

    public Trainer(BaseOptimizer optimizer, BaseEvaluator evaluator, Map<String, Object> kwargs) {
        this.optimizer = optimizer;
        this.evaluator = evaluator;
        
        Map<String, Object> options = kwargs != null ? kwargs : new HashMap<>();
        this.numParallel = (int) options.getOrDefault("num_parallel", TuneConstant.DEFAULT_PARALLEL_NUM);
        TuneUtils.validateDigitalParameter(this.numParallel, "num_parallel",
                TuneConstant.MIN_PARALLEL_NUM, TuneConstant.MAX_PARALLEL_NUM);
        
        this.earlyStopScore = (double) options.getOrDefault("early_stop_score", TuneConstant.DEFAULT_EARLY_STOP_SCORE);
        TuneUtils.validateDigitalParameter(this.earlyStopScore, "early_stop_score", 0.0, 1.0);
        
        this.callbacks = new Callbacks();
    }

    /**
     * Creates a Trainer with default options.
     */
    public Trainer(BaseOptimizer optimizer, BaseEvaluator evaluator) {
        this(optimizer, evaluator, null);
    }

    public DefaultEvaluator getEvaluator() {
        return (DefaultEvaluator) evaluator;
    }

    public JointOptimizer getOptimizer() {
        return (JointOptimizer) optimizer;
    }

    public int getNumParallel() {
        return numParallel;
    }

    public double getEarlyStopScore() {
        return earlyStopScore;
    }

    /**
     * Trains the agent.
     *
     * @param agent the agent to train
     * @param trainCases the training cases
     * @param valCases the validation cases (optional)
     * @param kwargs additional options (num_iterations)
     * @return the trained agent
     */
    public BaseAgent train(BaseAgent agent, CaseLoader trainCases, CaseLoader valCases, Map<String, Object> kwargs) {
        if (!checkTrainable(agent)) {
            throw new IllegalStateException("Trainer only supports current Agent right now");
        }

        Progress progress = preTrain(agent, kwargs);
        CaseLoader validationCases = valCases != null ? valCases : trainCases;

        // Initial evaluation
        var initialResult = evaluate(agent, validationCases);
        progress.setCurrentEpochScore(initialResult.score());
        progress.setBestScore(initialResult.score());

        callbacks.onTrainBegin(agent, progress, initialResult.evaluatedCases());

        if (progress.getBestScore() >= earlyStopScore) {
            Loggers.AGENT.info("Val set score {} already exceed target score {}, skip optimization",
                    progress.getBestScore(), earlyStopScore);
            callbacks.onTrainEnd(agent, progress, initialResult.evaluatedCases());
            return agent;
        }

        Loggers.AGENT.info("Val set baseline score: {}", progress.getCurrentEpochScore());

        ParameterSearcher searcher = new ParameterSearcher(this, validationCases);

        for (Integer epoch : progress.runEpoch().toList()) {
            callbacks.onTrainEpochBegin(agent, progress);

            try (BaseOptimizer opt = optimizer) {
                var trainResult = evaluate(agent, trainCases);
                Loggers.AGENT.info("Train epoch {}, train set score: {}", epoch, trainResult.score());
            }

            Map<String, LLMCall> currentParams = getLlmCalls(agent);
            Map<String, LLMCall> bestBatchParams = currentParams;

            for (Integer batch : progress.runBatch().toList()) {
                optimizer.backward(initialResult.evaluatedCases());
                optimizer.update();

                var searchResult = searcher.searchBest(
                        agent, progress.getBestScore(), currentParams,
                        List.of(getLlmCalls(agent))
                );

                if (searchResult.score() > progress.getBestBatchScore()) {
                    progress.setBestBatchScore(searchResult.score());
                    bestBatchParams = searchResult.parameters();
                }
            }

            Loggers.AGENT.info("Train epoch {}, val set score: {}", epoch, progress.getBestBatchScore());

            if (progress.getBestBatchScore() > progress.getBestScore()) {
                progress.setBestScore(progress.getBestBatchScore());
                updateAgent(agent, bestBatchParams);
            } else {
                updateAgent(agent, currentParams);
            }

            callbacks.onTrainEpochEnd(agent, progress, initialResult.evaluatedCases());

            if (progress.getBestScore() >= earlyStopScore) {
                break;
            }
        }

        callbacks.onTrainEnd(agent, progress, initialResult.evaluatedCases());
        return agent;
    }

    /**
     * Evaluates the agent.
     *
     * @param agent the agent to evaluate
     * @param cases the cases
     * @return the evaluation result
     */
    public EvalResult evaluate(BaseAgent agent, CaseLoader cases) {
        List<Case> caseList = cases.getCases();
        if (caseList.isEmpty()) {
            return new EvalResult(0.0, List.of());
        }

        List<Map<String, Object>> predicts = predict(agent, cases);
        List<EvaluatedCase> evaluatedCases = evaluator.batchEvaluate(caseList, predicts);
        
        double score = evaluatedCases.isEmpty() ? 0.0 :
                evaluatedCases.stream().mapToDouble(EvaluatedCase::getScore).average().orElse(0.0);
        
        return new EvalResult(score, evaluatedCases);
    }

    /**
     * Predicts outputs for cases.
     */
    public List<Map<String, Object>> predict(BaseAgent agent, CaseLoader cases) {
        List<Map<String, Object>> results = new ArrayList<>();
        int workers = Math.min(numParallel, cases.size());
        
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        
        try {
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            
            for (Case case_ : cases.getCases()) {
                CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        Map<String, Object> inputs = new HashMap<>(case_.getInputs());
                        inputs.put("conversation_id", case_.getCaseId());
                        @SuppressWarnings("unchecked")
                        Map<String, Object> output = (Map<String, Object>) agent.invoke(inputs, null);
                        return output;
                    } catch (Exception e) {
                        return Map.of("error", "Get wrong result due to " + e.getMessage());
                    }
                }, executor);
                futures.add(future);
            }
            
            for (CompletableFuture<Map<String, Object>> future : futures) {
                results.add(future.join());
            }
        } finally {
            executor.shutdown();
        }
        
        return results;
    }

    /**
     * Sets callbacks.
     */
    public void setCallbacks(Callbacks callbacks) {
        if (callbacks == null) {
            Loggers.AGENT.warn("callbacks should be a Callbacks object");
            return;
        }
        this.callbacks = callbacks;
    }

    private static void validateLegacyRange(double value, String name, double min, double max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " should be between " + min + " and " + max);
        }
    }

    private Progress preTrain(BaseAgent agent, Map<String, Object> kwargs) {
        int maxEpoch = kwargs != null 
                ? (int) kwargs.getOrDefault("num_iterations", TuneConstant.DEFAULT_ITERATION_NUM)
                : TuneConstant.DEFAULT_ITERATION_NUM;
        
        TuneUtils.validateDigitalParameter(maxEpoch, "num_iterations",
                TuneConstant.MIN_ITERATION_NUM, TuneConstant.MAX_ITERATION_NUM);
        
        Progress progress = Progress.builder().maxEpoch(maxEpoch).build();
        optimizer.bindParameter(getLlmCalls(agent));
        
        return progress;
    }

    public void updateAgent(BaseAgent agent, Map<String, ?> parameters) {
        if (parameters == null) {
            return;
        }
        
        Map<String, LLMCall> agentParams = getLlmCalls(agent);
        for (Map.Entry<String, LLMCall> entry : agentParams.entrySet()) {
            String name = entry.getKey();
            Object param = parameters.get(name);
            
            if (param instanceof TextualParameter tp) {
                entry.getValue().updateSystemPrompt(
                        TuneUtils.getContentStringFromTemplate(tp.getLlmCall().getSystemPrompt()));
                entry.getValue().updateUserPrompt(
                        TuneUtils.getContentStringFromTemplate(tp.getLlmCall().getUserPrompt()));
            } else if (param instanceof LLMCall llmCall) {
                entry.getValue().updateSystemPrompt(TuneUtils.getContentStringFromTemplate(llmCall.getSystemPrompt()));
                entry.getValue().updateUserPrompt(TuneUtils.getContentStringFromTemplate(llmCall.getUserPrompt()));
            }
        }
    }

    private boolean checkTrainable(BaseAgent agent) {
        try {
            agent.getClass().getMethod("getLlmCalls");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, LLMCall> getLlmCalls(BaseAgent agent) {
        try {
            return (Map<String, LLMCall>) agent.getClass().getMethod("getLlmCalls").invoke(agent);
        } catch (Exception e) {
            throw new IllegalStateException("Agent does not expose getLlmCalls()", e);
        }
    }

    /**
     * Evaluation result record.
     */
    public record EvalResult(double score, List<EvaluatedCase> evaluatedCases) {}
}
