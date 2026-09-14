/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors Python's {@code ReMeRetrieveRequest} in
 * {@code openjiuwen/extensions/context_evolver/schema/io_schema.py}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReMeRetrieveRequest {

    private String query;
    @JsonProperty("topk_retrieval")
    private int topkRetrieval = 10;
    @JsonProperty("topk_rerank")
    private int topkRerank = 5;
}
