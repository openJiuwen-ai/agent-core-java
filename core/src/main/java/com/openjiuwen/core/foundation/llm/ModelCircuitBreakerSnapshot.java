/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

/**
 * Immutable diagnostic sample of a model circuit breaker.
 *
 * <p>The fields are sampled independently and do not form an atomic view during
 * concurrent updates. A retained snapshot does not change with the breaker.
 * Exact state assertions should sample after calls have completed.</p>
 *
 * @param isOpen whether the fail-fast window was active when sampled
 * @param consecutiveFailures counted failures since the most recent success;
 *     ignored failures and window expiry do not clear this value
 * @since 0.1.17
 */
public record ModelCircuitBreakerSnapshot(boolean isOpen, int consecutiveFailures) {
}
