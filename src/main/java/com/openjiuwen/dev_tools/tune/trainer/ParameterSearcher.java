/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.trainer;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.singleagent.legacy.LegacyBaseAgent;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.dataset.CaseLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parameter candidate search helper.
 *
 * <p>Mirrors Python's {@code ParameterSearcher} in
 * {@code openjiuwen/dev_tools/tune/trainer/trainer.py}.</p>
 */
public class ParameterSearcher {
    private final Trainer trainer;
    private final CaseLoader caseLoader;

    public ParameterSearcher(Trainer trainer, CaseLoader caseLoader) {
        this.trainer = Objects.requireNonNull(trainer, "trainer");
        this.caseLoader = Objects.requireNonNull(caseLoader, "caseLoader");
    }

    public SearchResult searchBest(LegacyBaseAgent agent,
                                   double baseScore,
                                   Map<String, ?> baseParameters,
                                   List<? extends Map<String, ?>> parameters) {
        List<Map<String, ?>> candidateGroups = new ArrayList<>();
        candidateGroups.add(baseParameters);
        if (parameters != null) {
            candidateGroups.addAll(parameters);
        }
        List<Map<String, ?>> candidates = new ArrayList<>(generateCandidates(candidateGroups));
        if (!candidates.isEmpty()) {
            candidates.remove(0);
        }
        Collections.shuffle(candidates);
        int sampleSize = Math.min(Trainer.DEFAULT_CANDIDATES_SAMPLE_NUM, candidates.size());
        candidates = new ArrayList<>(candidates.subList(0, sampleSize));
        double bestScore = baseScore;
        Map<String, ?> bestParameters = Trainer.snapshotParameterMap(baseParameters);
        List<EvaluatedCase> bestCases = null;
        double lastScore = baseScore;
        Loggers.AGENT.info("start searching best parameter group from {} candidates, current epoch baseline score: {}",
                candidates.size(), bestScore);
        int index = 0;
        for (Map<String, ?> candidate : candidates) {
            trainer.updateAgent(agent, candidate);
            Trainer.EvalResult result = trainer.evaluate(agent, caseLoader);
            Loggers.AGENT.info("finish evaluating candidate {}, score {}", index, result.score());
            if (result.score() > bestScore) {
                bestScore = result.score();
                bestParameters = candidate;
                bestCases = result.evaluatedCases();
            }
            if (bestCases == null) {
                bestCases = result.evaluatedCases();
            }
            lastScore = result.score();
            index += 1;
        }
        return new SearchResult(bestScore, bestParameters, bestCases, lastScore);
    }

    public static List<Map<String, ?>> generateCandidates(List<? extends Map<String, ?>> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return List.of();
        }
        Map<String, ?> first = parameters.getFirst();
        int nParams = first.size();
        int nCandidates = parameters.size();
        List<String> nodeNames = new ArrayList<>(first.keySet());
        List<Map<String, ?>> allCandidates = new ArrayList<>();
        generateCandidatesRecursively(parameters, nodeNames, nParams, nCandidates, 0,
                new LinkedHashMap<>(), allCandidates);
        return allCandidates;
    }

    private static void generateCandidatesRecursively(List<? extends Map<String, ?>> parameters,
                                                      List<String> nodeNames,
                                                      int nParams,
                                                      int nCandidates,
                                                      int iParam,
                                                      Map<String, Trainer.PromptSnapshot> candidate,
                                                      List<Map<String, ?>> allCandidates) {
        if (iParam == nParams) {
            allCandidates.add(new LinkedHashMap<>(candidate));
            return;
        }
        String nodeName = nodeNames.get(iParam);
        for (int iCd = 0; iCd < nCandidates; iCd++) {
            Map<String, ?> source = parameters.get(iCd);
            if (!source.containsKey(nodeName)) {
                throw new IllegalArgumentException("candidate parameter group missing node " + nodeName);
            }
            Trainer.PromptSnapshot snapshot = Trainer.PromptSnapshot.from(source.get(nodeName));
            if (snapshot != null) {
                candidate.put(nodeName, snapshot);
            }
            generateCandidatesRecursively(parameters, nodeNames, nParams, nCandidates,
                    iParam + 1, candidate, allCandidates);
            candidate.remove(nodeName);
        }
    }

    /**
     * Mirrors Python's {@code ParameterSearcher.search_best} tuple return in
     * {@code openjiuwen/dev_tools/tune/trainer/trainer.py}.
     */
    public record SearchResult(double score,
                               Map<String, ?> parameters,
                               List<EvaluatedCase> evaluatedCases,
                               double lastScore) {
    }
}
