/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.legacy.llm_call;

import java.util.Map;

/**
 * Callback invoked after a legacy LLM call completes.
 *
 * <p>Mirrors Python's optimizer callback callable in
 * {@code openjiuwen/core/operator/legacy/llm_call/base.py}.</p>
 */
@FunctionalInterface
public interface LegacyOptimizerCallback {

    void onComplete(String llmCallId, Map<String, Object> inputs, Object response, Object session) throws Exception;
}
