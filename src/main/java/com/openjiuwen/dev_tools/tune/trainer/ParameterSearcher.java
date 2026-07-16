/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.trainer;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.core.singleagent.legacy.BaseAgent;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.dataset.CaseLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parameter searcher for finding best parameter combinations.
 * <p>
 * Mirrors Python's {@code ParameterSearcher} in {@code openjiuwen.dev_tools.tune.trainer.trainer}.
 * 
 * @since 0.1.7
 */
public class ParameterSearcher {
    private static final int DEFAULT_CANDIDATES_SAMPLE_NUM = 6;

    private final Trainer trainer;
    private final CaseLoader caseLoader;

    /**
     * Creates a ParameterSearcher.
     * 
     * @param trainer the trainer
     * @param caseLoader the case loader
     * @since 0.1.7
     */
    public ParameterSearcher(Trainer trainer, CaseLoader caseLoader) {
        this.trainer = trainer;
        this.caseLoader = caseLoader;
    }

    /**
     * Searches for the best parameter combination.
     * 
     * @param agent the agent
     * @param baseScore the base score to beat
     * @param baseParameters the base parameters
     * @param parameters the list of parameter candidates
     * @return the search result
     * @since 0.1.7
     */
    public SearchResult searchBest(BaseAgent agent, double baseScore, Map<String, LLMCall> baseParameters,
            List<Map<String, LLMCall>> parameters) {
        List<Map<String, LLMCall>> candidates = generateCandidates(new ArrayList<>(List.of(baseParameters)) {
            {
                addAll(parameters);
            }
        });
        candidates.remove(0); // Remove base

        int sampleSize = Math.min(DEFAULT_CANDIDATES_SAMPLE_NUM, candidates.size());
        Collections.shuffle(candidates);
        candidates = candidates.subList(0, sampleSize);

        double bestScore = baseScore;
        Map<String, LLMCall> bestParams = baseParameters;
        List<EvaluatedCase> bestCases = null;

        Loggers.AGENT.info("Start searching best parameter group from {} candidates, current baseline score: {}",
                candidates.size(), bestScore);

        double lastScore = baseScore;
        int i = 0;

        for (Map<String, LLMCall> candidate : candidates) {
            trainer.updateAgent(agent, candidate);
            Trainer.EvalResult result = trainer.evaluate(agent, caseLoader);

            Loggers.AGENT.info("Finish evaluating candidate {}, score {}", i, result.score());

            if (result.score() > bestScore) {
                bestScore = result.score();
                bestParams = candidate;
                bestCases = result.evaluatedCases();
            }

            if (bestCases == null) {
                bestCases = result.evaluatedCases();
            }

            lastScore = result.score();
            i++;
        }

        return new SearchResult(bestScore, bestParams, bestCases, lastScore);
    }

    /**
     * Generates all possible parameter combinations.
     * 
     * @param parameters the list of parameter maps
     * @return all possible combinations
     * @since 0.1.7
     */
    public static List<Map<String, LLMCall>> generateCandidates(List<Map<String, LLMCall>> parameters) {
        if (parameters.isEmpty()) {
            return List.of();
        }

        int nParams = parameters.get(0).size();
        int nCandidates = parameters.size();
        List<String> nodeNames = new ArrayList<>(parameters.get(0).keySet());
        List<Map<String, LLMCall>> allCandidates = new ArrayList<>();

        generateCandidatesRecursively(parameters, nodeNames, nParams, nCandidates, 0, new HashMap<>(), allCandidates);

        return allCandidates;
    }

    /**
     * generateCandidatesRecursively.
     * 
     * @param parameters parameters
     * @param nodeNames nodeNames
     * @param nParams nParams
     * @param nCandidates nCandidates
     * @param iParam iParam
     * @param current current
     * @param result result
     * @since 0.1.7
     */
    private static void generateCandidatesRecursively(List<Map<String, LLMCall>> parameters, List<String> nodeNames,
            int nParams, int nCandidates, int iParam, Map<String, LLMCall> current, List<Map<String, LLMCall>> result) {
        if (iParam == nParams) {
            result.add(new HashMap<>(current));
            return;
        }

        for (int iCd = 0; iCd < nCandidates; iCd++) {
            String nodeName = nodeNames.get(iParam);
            current.put(nodeName, parameters.get(iCd).get(nodeName));
            generateCandidatesRecursively(parameters, nodeNames, nParams, nCandidates, iParam + 1, current, result);
            current.remove(nodeName);
        }
    }

    /**
     * Search result record.
     * 
     * @since 0.1.7
     */
    public record SearchResult(double score, Map<String, LLMCall> parameters, List<EvaluatedCase> evaluatedCases,
            double lastScore) {
    }
}
