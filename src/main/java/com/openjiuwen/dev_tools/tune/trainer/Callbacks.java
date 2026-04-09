/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.trainer;

import com.openjiuwen.core.singleagent.legacy.BaseAgent;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;

import java.util.List;

/**
 * Training callback hooks for lifecycle events.
 *
 * <p>Mirrors Python's {@code Callbacks} in {@code openjiuwen.dev_tools.tune.trainer.base}.
 */
public class Callbacks {

    /**
     * Called at the beginning of training.
     *
     * @param agent the agent being trained
     * @param progress the training progress
     * @param evalInfo the evaluation information
     */
    public void onTrainBegin(BaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
        // Default implementation does nothing
    }

    /**
     * Called at the end of training.
     *
     * @param agent the agent being trained
     * @param progress the training progress
     * @param evalInfo the evaluation information
     */
    public void onTrainEnd(BaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
        // Default implementation does nothing
    }

    /**
     * Called at the beginning of each epoch.
     *
     * @param agent the agent being trained
     * @param progress the training progress
     */
    public void onTrainEpochBegin(BaseAgent agent, Progress progress) {
        // Default implementation does nothing
    }

    /**
     * Called at the end of each epoch.
     *
     * @param agent the agent being trained
     * @param progress the training progress
     * @param evalInfo the evaluation information
     */
    public void onTrainEpochEnd(BaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
        // Default implementation does nothing
    }
}