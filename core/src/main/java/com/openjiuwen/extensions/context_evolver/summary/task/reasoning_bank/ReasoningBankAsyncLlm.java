/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reasoning_bank;

import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code llm.async_generate(..., system_prompt=...)} usage in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/update.py}.
 */
public interface ReasoningBankAsyncLlm {

    CompletableFuture<String> asyncGenerate(String prompt, String systemPrompt);
}
