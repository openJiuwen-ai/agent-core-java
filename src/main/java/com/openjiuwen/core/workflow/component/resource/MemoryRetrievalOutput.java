/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.memory.MemResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Output schema for memory retrieval component.
 * <p>
 * Mirrors Python's {@code MemoryRetrievalOutput}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRetrievalOutput {

    @Builder.Default
    private List<MemResult> fragmentMemoryResults = new ArrayList<>();
    
    @Builder.Default
    private List<MemResult> summaryResults = new ArrayList<>();
}