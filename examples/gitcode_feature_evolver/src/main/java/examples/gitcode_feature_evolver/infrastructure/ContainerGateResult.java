/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import java.util.List;
import java.util.Objects;

/**
 * Result of one fixed credential-free container verification profile.
 *
 * @param outcome stable result classification
 * @param exitCode container process exit code
 * @param output bounded sanitized output
 * @param command redacted fixed command description
 * @since 0.1.12
 */
public record ContainerGateResult(Outcome outcome, int exitCode, String output,
                                  List<String> command) {
    /** Freeze and normalize the result. */
    public ContainerGateResult {
        outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        output = output == null ? "" : output;
        command = command == null ? List.of() : List.copyOf(command);
    }

    /** @return whether a non-RED verification profile passed */
    public boolean passed() {
        return outcome == Outcome.PASSED;
    }

    /** @return whether RED produced a trustworthy test failure */
    public boolean expectedRed() {
        return outcome == Outcome.EXPECTED_RED;
    }

    /** Stable container-gate outcomes. */
    public enum Outcome {
        PASSED,
        EXPECTED_RED,
        TEST_FAILED,
        DEPENDENCY_MISSING,
        INFRASTRUCTURE_FAILED,
        TIMED_OUT
    }
}
