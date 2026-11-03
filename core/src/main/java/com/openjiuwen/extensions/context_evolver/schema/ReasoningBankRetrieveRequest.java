/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors Python's {@code ReasoningBankRetrieveRequest} in
 * {@code openjiuwen/extensions/context_evolver/schema/io_schema.py}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReasoningBankRetrieveRequest {

    private String query;
    private int topk = 5;
}
