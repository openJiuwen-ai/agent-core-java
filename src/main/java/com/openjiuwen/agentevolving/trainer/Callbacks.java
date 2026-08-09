/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trainer;

import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.core.singleagent.BaseAgent;

import java.util.List;

/**
 * Training lifecycle hooks for self-evolving trainer integrations.
 *
 * <p>Mirrors Python's {@code Callbacks} in
 * {@code openjiuwen/agent_evolving/trainer/progress.py}.</p>
 */
public class Callbacks {

    public void onTrainBegin(BaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
    }

    public void onTrainEnd(BaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
    }

    public void onTrainEpochBegin(BaseAgent agent, Progress progress) {
    }

    public void onTrainEpochEnd(BaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
    }
}
