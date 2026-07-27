/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.agent_rl.offline.coordinator.test_training_coordinator} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/offline/coordinator/test_training_coordinator.py}.</p>
 */
class TrainingCoordinatorPythonSkipTest {
    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: could not import 'torch': No module named 'torch'";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void trainingCoordinatorCollectionRequiresTorch() {
    }
}
