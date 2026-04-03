// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.trainer;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;

import java.util.List;

/**
 * Training lifecycle hooks.
 *
 * <p>Subclass can override to integrate logging, early stopping, metric reporting, etc.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.trainer.progress.Callbacks}.
 */
public class Callbacks {

    /**
     * Training begin (validation baseline evaluation completed).
     *
     * @param agent    Agent being trained
     * @param progress Training progress
     * @param evalInfo Evaluation results
     */
    public void onTrainBegin(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
        // Default: no-op
    }

    /**
     * Training end.
     *
     * @param agent    Agent being trained
     * @param progress Training progress
     * @param evalInfo Evaluation results
     */
    public void onTrainEnd(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
        // Default: no-op
    }

    /**
     * Single epoch training begins.
     *
     * @param agent    Agent being trained
     * @param progress Training progress
     */
    public void onTrainEpochBegin(Object agent, Progress progress) {
        // Default: no-op
    }

    /**
     * Single epoch training ends (best_score updated / parameters written back).
     *
     * @param agent    Agent being trained
     * @param progress Training progress
     * @param evalInfo Evaluation results
     */
    public void onTrainEpochEnd(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
        // Default: no-op
    }
}