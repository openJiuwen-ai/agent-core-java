/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

/**
 * Guarantee decision of the turn-dimension run budget, resolved once per invocation.
 *
 * <p>Carries the effective guaranteed rounds, the source of the value
 * ({@link #SOURCE_CONFIGURED} or {@link #SOURCE_DEFAULT}) and the absolute hard limit.
 * Defined by FEAT-057 (main loop run budget and progress tracking).</p>
 *
 * @param guaranteedRounds effective guaranteed rounds for this invocation
 * @param source value source, {@link #SOURCE_CONFIGURED} or {@link #SOURCE_DEFAULT}
 * @param hardLimit absolute turn ceiling, explicit or {@code max(guaranteedRounds * 2, 200)}
 * @since 2026-10-08
 */
public record GuaranteeDecision(int guaranteedRounds, String source, int hardLimit) {
    /** The guarantee rounds came from an explicit configured suggestion. */
    public static final String SOURCE_CONFIGURED = "configured";

    /** The guarantee rounds came from the configurable default. */
    public static final String SOURCE_DEFAULT = "default";
}
