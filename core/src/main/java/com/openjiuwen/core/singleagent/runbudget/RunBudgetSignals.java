/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;

/**
 * Host-facing injection surface for "no substantive progress" facts (FEAT-057).
 *
 * <p>Host assembly rails (for example deepanalyze-java saturation/loop detectors) call
 * {@link #reportNoProgress} from their own hooks to feed host tool-semantics evidence into
 * the budget checkpoint evaluation. When no budget state is present (budget rail not
 * registered), the report is silently ignored.</p>
 *
 * @since 2026-10-08
 */
public final class RunBudgetSignals {
    private RunBudgetSignals() {
    }

    /**
     * Reports a host-detected no-progress fact for the current checkpoint window.
     *
     * @param ctx callback context of the reporting hook
     * @param evidence host evidence text (for observability)
     * @param promptText host-provided prompt wording, may be null for the core default
     */
    public static void reportNoProgress(AgentCallbackContext ctx, String evidence, String promptText) {
        if (ctx == null) {
            return;
        }
        RunBudgetState.from(ctx.getExtra())
                .ifPresent(state -> state.reportHostStall(evidence, promptText));
    }
}
