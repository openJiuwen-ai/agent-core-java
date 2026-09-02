/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.legacy.llm_call;

import com.openjiuwen.core.session.Session;

import java.util.Map;

/**
 * Callback for the legacy LLMCall compatibility path.
 * 
 * @since 0.1.7
 */
@FunctionalInterface
public interface LegacyOptimizerCallback {
    /**
     * onComplete.
     * 
     * @param llmCallId llmCallId
     * @param inputs inputs
     * @param response response
     * @param session session
     * @throws Exception Exception
     * @since 0.1.7
     */
    void onComplete(String llmCallId, Map<String, Object> inputs, Object response, Session session) throws Exception;
}
