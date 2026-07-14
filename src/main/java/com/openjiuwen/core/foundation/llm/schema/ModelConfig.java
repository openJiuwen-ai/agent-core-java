/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code ModelConfig} in
 * {@code openjiuwen/core/foundation/llm/schema/mode_info.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelConfig {

    private String modelProvider;

    @Builder.Default
    private BaseModelInfo modelInfo = new BaseModelInfo();

    public String modelProvider() {
        return modelProvider;
    }

    public BaseModelInfo modelInfo() {
        return modelInfo;
    }
}
