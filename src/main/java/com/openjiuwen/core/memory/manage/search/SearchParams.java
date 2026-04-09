/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Parameters for memory search operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchParams {
    private String userId;
    private String scopeId;
    private String query;
    @Builder.Default
    private int topK = 5;
    @Builder.Default
    private double threshold = 0.3;
    private String searchType;
}
