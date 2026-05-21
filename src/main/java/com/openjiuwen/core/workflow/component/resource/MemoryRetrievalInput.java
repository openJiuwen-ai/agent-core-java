/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Input schema for memory retrieval component.
 * <p>
 * Mirrors Python's {@code MemoryRetrievalInput}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRetrievalInput {

    private String query;
    
    @Builder.Default
    private int topK = 5;
    
    // Allow additional fields
    private Map<String, Object> additionalFields;
}