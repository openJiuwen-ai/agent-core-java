/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trainer;

import com.openjiuwen.agentevolving.dataset.EvaluatedCase;

import java.util.List;

/**
 * Training lifecycle hooks.
 * <p>
 * Subclass can override to integrate logging, early stopping, metric reporting, etc.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.trainer.progress.Callbacks}.
 * 
 * @since 0.1.7
 */
public class Callbacks {
    /**
     * onTrainBegin.
     * 
     * @param agent agent
     * @param progress progress
     * @param evalInfo evalInfo
     * @since 0.1.7
     */
    public void onTrainBegin(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
        // Default: no-op
    }

    /**
     * Training end.
     * 
     * @param agent Agent being trained
     * @param progress Training progress
     * @param evalInfo Evaluation results
     * @since 0.1.7
     */
    public void onTrainEnd(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
        // Default: no-op
    }

    /**
     * Single epoch training begins.
     * 
     * @param agent Agent being trained
     * @param progress Training progress
     * @since 0.1.7
     */
    public void onTrainEpochBegin(Object agent, Progress progress) {
        // Default: no-op
    }

    /**
     * Single epoch training ends (best_score updated / parameters written back).
     * 
     * @param agent Agent being trained
     * @param progress Training progress
     * @param evalInfo Evaluation results
     * @since 0.1.7
     */
    public void onTrainEpochEnd(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
        // Default: no-op
    }
}
