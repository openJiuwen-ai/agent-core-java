/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.List;

/**
 * Parameters for memory extraction.
 * Corresponds to Python: process/extract/generation.py ExtractMemoryParams
 */
public record ExtractMemoryParams(
        String userId,
        String scopeId,
        List<BaseMessage> messages,
        List<BaseMessage> historyMessages,
        Pair<String, Model> baseChatModel
) {
}

