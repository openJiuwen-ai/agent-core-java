/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a field to be extracted by the Questioner component.
 * <p>
 * Mirrors Python's {@code FieldInfo} in
 * {@code openjiuwen/core/workflow/components/llm/questioner_comp.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldInfo {
    private String fieldName;
    private String description;
    @Builder.Default
    private String type = "string";
    @Builder.Default
    private String cnFieldName = "";
    @Builder.Default
    private boolean required = false;
    @Builder.Default
    private Object defaultValue = "";
}
