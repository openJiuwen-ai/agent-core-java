/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reme;

import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code llm.async_generate} boundary in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reme/run.py}.
 */
public interface ReMeAsyncLlm {

    CompletableFuture<String> asyncGenerate(String prompt);
}
