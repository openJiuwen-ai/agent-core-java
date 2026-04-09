/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.operator.legacy.llm_call;

import com.openjiuwen.core.session.Session;

import java.util.Map;

/**
 * Callback for the legacy LLMCall compatibility path.
 */
@FunctionalInterface
public interface LegacyOptimizerCallback {

    void onComplete(String llmCallId, Map<String, Object> inputs, Object response, Session session) throws Exception;
}
