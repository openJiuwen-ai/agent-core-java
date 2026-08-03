/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.List;
import java.util.Map;

/**
 * Immutable input for building a context processor state.
 *
 * <p>Mirrors Python's {@code ContextProcessorStateInput} in
 * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
 */
public record ContextProcessorStateInput(String operationId, String status, String phase, String trigger,
                                         ContextProcessorPort processor, String reason,
                                         List<BaseMessage> beforeMessages, List<BaseMessage> afterMessages,
                                         double startedAt, Double endedAt, String error,
                                         List<Integer> messagesToModify, boolean force, Integer contextMax,
                                         String compactSummary, Map<String, Object> compressionUsage) {

    /**
     * Narrow context processor adapter used by the recorder.
     *
     * <p>Mirrors Python's {@code ContextProcessor} dependency in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    public interface ContextProcessorPort {
        String processorType();
    }
}
