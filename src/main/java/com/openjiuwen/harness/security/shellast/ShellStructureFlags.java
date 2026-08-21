/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.shellast;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural flags scanned from a shell command.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.shell_ast.ShellStructureFlags}.
 * Boolean accessors follow the project {@code is*} convention (see
 * {@code PermissionCheckResult.isNeedsApproval}). {@link #hasRiskyStructure()} drives
 * the conservative fail-closed upgrade to ASK whenever the fallback scanner cannot
 * trust a command's structure.
 *
 * @since 0.1.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShellStructureFlags {
    private boolean isCompoundOperators;
    private boolean isPipeline;
    private boolean isSubshell;
    private boolean isCommandGroup;
    private boolean isCommandSubstitution;
    private boolean isProcessSubstitution;
    private boolean isParameterExpansion;
    private boolean isHeredoc;
    private boolean isInputRedirection;
    private boolean isOutputRedirection;
    private boolean isActualOperatorNodes;
    @Builder.Default
    private List<String> operators = new ArrayList<>();

    /**
     * Whether the command uses any structure the conservative scanner cannot safely analyze.
     *
     * @return true when any risky structural flag is set
     * @since 0.1.15
     */
    public boolean hasRiskyStructure() {
        return isPipeline
                || isCompoundOperators
                || isSubshell
                || isCommandGroup
                || isCommandSubstitution
                || isProcessSubstitution
                || isParameterExpansion
                || isHeredoc
                || isInputRedirection
                || isOutputRedirection;
    }
}
