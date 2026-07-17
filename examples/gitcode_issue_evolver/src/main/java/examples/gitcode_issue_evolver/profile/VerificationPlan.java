/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.profile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Commands and retry policy used to verify repository changes.
 *
 * @param commands executable argument lists
 * @param timeout maximum duration for one complete verification-plan run
 * @param maxFixAttempts maximum Agent repair attempts
 * @since 0.1.12
 */
public record VerificationPlan(List<List<String>> commands, Duration timeout, int maxFixAttempts) {
    public VerificationPlan(List<List<String>> commands, Duration timeout, int maxFixAttempts) {
        List<List<String>> copied = new ArrayList<>();
        if (commands != null) {
            commands.forEach(command -> copied.add(List.copyOf(command)));
        }
        this.commands = List.copyOf(copied);
        this.timeout = timeout == null ? Duration.ofMinutes(20) : timeout;
        this.maxFixAttempts = Math.max(0, maxFixAttempts);
    }
}
