/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.List;

/**
 * Input snapshot for building a compression state event.
 * <p>
 * Mirrors Python's {@code ContextProcessorStateInput}.
 */
public record ContextProcessorStateInput(
        String operationId,
        String status,
        String phase,
        String trigger,
        ContextProcessor processor,
        String reason,
        List<BaseMessage> beforeMessages,
        List<BaseMessage> afterMessages,
        double startedAt,
        Double endedAt,
        String error,
        List<Integer> messagesToModify,
        boolean isForce,
        Integer contextMax
) {
}
