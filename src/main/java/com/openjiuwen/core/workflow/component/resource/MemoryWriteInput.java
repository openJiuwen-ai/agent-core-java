/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Input schema for memory write component.
 * <p>
 * Mirrors Python's {@code MemoryWriteInput}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryWriteInput {

    private List<BaseMessage> messages;
    private OffsetDateTime timestamp;
    
    // Allow additional fields
    private Map<String, Object> additionalFields;
}