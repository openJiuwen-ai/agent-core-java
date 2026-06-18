/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.trainer;

import com.openjiuwen.core.single_agent.legacy.LegacyBaseAgent;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;

import java.util.List;

/**
 * Training lifecycle callback hooks.
 *
 * <p>Mirrors Python's {@code Callbacks} in
 * {@code openjiuwen/dev_tools/tune/trainer/base.py}.</p>
 */
public class Callbacks {

    public void onTrainBegin(LegacyBaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
    }

    public void onTrainEnd(LegacyBaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
    }

    public void onTrainEpochBegin(LegacyBaseAgent agent, Progress progress) {
    }

    public void onTrainEpochEnd(LegacyBaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
    }
}
