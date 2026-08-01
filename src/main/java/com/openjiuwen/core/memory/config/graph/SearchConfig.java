/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config.graph;

import com.openjiuwen.spi.store.query.QueryExpr;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Config for searching memory.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchConfig extends BaseStrategy {
    private int bfsK = 3;
    private int bfsDepth = 0;
    private QueryExpr filterExpr;
    private List<String> outputFields;
    private boolean isRerank = false;
    private String language = "en";
}
