/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import java.util.concurrent.CompletableFuture;

/**
 * LLM boundary used by MaTTS operations.
 *
 * <p>Mirrors Python's {@code llm.async_generate} calls in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/matts.py}.</p>
 */
public interface MattsAsyncLlm {

    CompletableFuture<String> asyncGenerate(String prompt);

    default CompletableFuture<String> asyncGenerate(String prompt, double temperature) {
        return asyncGenerate(prompt);
    }
}
