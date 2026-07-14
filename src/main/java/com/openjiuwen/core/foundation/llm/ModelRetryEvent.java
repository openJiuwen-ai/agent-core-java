/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import java.time.Duration;

/**
 * Describes one retry that is about to be attempted for a model invocation.
 *
 * @param retryCount retry number, starting at one
 * @param maxRetries configured maximum number of retries
 * @param statusCode HTTP status that triggered the retry, if applicable
 * @param exceptionType exception class that triggered the retry, if applicable
 * @param delay actual delay applied before the retry
 * @param delaySource source used to determine the delay
 */
public record ModelRetryEvent(
        int retryCount,
        int maxRetries,
        Integer statusCode,
        String exceptionType,
        Duration delay,
        String delaySource) {
}
