/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

/** Receives model retry events for a single invocation. */
@FunctionalInterface
public interface ModelRetryListener {

    void onRetry(ModelRetryEvent event);
}
