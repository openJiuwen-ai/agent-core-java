/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.schema.Param;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent memory configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemoryConfig {

    @Builder.Default
    @JsonProperty("mem_variables")
    private List<Param> memVariables = new ArrayList<>();

    @Builder.Default
    @JsonProperty("enable_long_term_mem")
    private boolean enableLongTermMem = true;

    @Builder.Default
    @JsonProperty("enable_fragment_memory")
    private boolean enableFragmentMemory = true;

    @Builder.Default
    @JsonProperty("enable_summary_memory")
    private boolean enableSummaryMemory = true;
}
