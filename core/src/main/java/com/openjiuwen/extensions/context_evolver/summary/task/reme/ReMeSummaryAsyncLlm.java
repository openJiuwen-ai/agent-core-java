/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code llm.async_generate(prompt=...)} usage in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/update.py}.
 */
public interface ReMeSummaryAsyncLlm {

    CompletableFuture<String> asyncGenerate(String prompt);
}
