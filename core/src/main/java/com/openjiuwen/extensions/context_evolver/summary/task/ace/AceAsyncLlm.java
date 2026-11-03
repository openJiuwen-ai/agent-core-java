/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code llm.async_generate} usage in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/update.py}.
 */
public interface AceAsyncLlm {

    CompletableFuture<String> asyncGenerate(String prompt);
}
