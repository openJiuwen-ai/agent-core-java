/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import examples.gitcode_feature_evolver.infrastructure.RootlessContainerGateRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Computes startup readiness, including the mandatory isolated test executor.
 *
 * @since 0.1.12
 */
public final class FeatureReadiness {
    private FeatureReadiness() {
    }

    /**
     * Validate static configuration and rootless container runtime state.
     *
     * @param config resolved feature configuration
     * @param container isolated test runner
     * @return non-sensitive readiness failures
     */
    public static List<String> errors(FeatureEvolvingConfig config,
                                      RootlessContainerGateRunner container) {
        FeatureEvolvingConfig required = Objects.requireNonNull(config, "config must not be null");
        RootlessContainerGateRunner requiredContainer = Objects.requireNonNull(
                container, "container must not be null");
        List<String> errors = new ArrayList<>(required.readinessErrors());
        if (errors.isEmpty()) {
            errors.addAll(requiredContainer.readinessErrors());
        }
        return List.copyOf(errors);
    }
}
