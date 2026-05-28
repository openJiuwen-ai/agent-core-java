/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Immutable input record for the context-processor state recorder.
 * <p>
 * Mirrors Python's {@code ContextProcessorStateInput} dataclass from
 * {@code context_engine/context/processor_state_recorder.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextProcessorStateInput {

    private String operationId;
    private String status;
    private String phase;
    private String trigger;
    private ContextProcessor processor;
    private String reason;
    private List<BaseMessage> beforeMessages;
    private List<BaseMessage> afterMessages;
    private double startedAt;
    private Double endedAt;
    private String error;
    private List<Integer> messagesToModify;
    private boolean force;
    private Integer contextMax;
}
