/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class CIGateResult used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class CIGateResult {
    @Builder.Default
    private boolean isPassed = true;
    @Builder.Default
    private List<String> executedCommands = new ArrayList<>();
    @Builder.Default
    private List<String> gateOutputs = new ArrayList<>();
    @Builder.Default
    private List<Map<String, Object>> gates = new ArrayList<>();
    @Builder.Default
    private String errors = "";
    @Builder.Default
    private VerificationFailureType failureType = VerificationFailureType.NONE;

    /**
     * Create a compatibility result without an explicit failure type.
     *
     * @param isPassed whether all gates passed
     * @param executedCommands executed command descriptions
     * @param gateOutputs captured gate outputs
     * @param gates structured gate results
     * @param errors summarized errors
     */
    public CIGateResult(boolean isPassed, List<String> executedCommands,
                        List<String> gateOutputs, List<Map<String, Object>> gates, String errors) {
        this(isPassed, executedCommands, gateOutputs, gates, errors,
                isPassed ? VerificationFailureType.NONE : VerificationFailureType.CHECK_FAILED);
    }

    /**
     * Resolve a stable failure type for callers that receive a legacy result without one.
     *
     * @return resolved verification failure type
     */
    public VerificationFailureType resolvedFailureType() {
        if (isPassed) {
            return VerificationFailureType.NONE;
        }
        return failureType == null || failureType == VerificationFailureType.NONE
                ? VerificationFailureType.CHECK_FAILED : failureType;
    }
}
