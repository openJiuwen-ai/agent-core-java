/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code ACERetrieveResponse} in
 * {@code openjiuwen/extensions/context_evolver/schema/io_schema.py}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ACERetrieveResponse {

    private String status;
    @JsonProperty("memory_string")
    private String memoryString;
    @JsonProperty("retrieved_memory")
    private List<ACERetrievedMemory> retrievedMemory = new ArrayList<>();
}
