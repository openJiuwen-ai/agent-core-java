/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Result of a vector search operation.
 * <p>
 * Mirrors Python's {@code VectorSearchResult}.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@AllArgsConstructor
public class VectorSearchResult {
    private final double score;

    /**
     * All field values from the matched document.
     * 
     * @since 0.1.7
     */
    @Builder.Default
    private final Map<String, Object> fields = Map.of();
}
