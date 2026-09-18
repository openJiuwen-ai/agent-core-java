/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code ReasoningBankSummarizeResponse} in
 * {@code openjiuwen/extensions/context_evolver/schema/io_schema.py}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReasoningBankSummarizeResponse {

    private String status;
    private List<ReasoningBankMemory> memory = new ArrayList<>();
}
