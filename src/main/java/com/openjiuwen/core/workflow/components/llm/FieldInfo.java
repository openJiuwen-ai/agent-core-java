/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.components.llm;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.FieldInfo}
 * with a positional 3-arg constructor for test compatibility.
 * Extends the base FieldInfo so instances are accepted wherever base FieldInfo is expected.
 */
public class FieldInfo extends com.openjiuwen.core.workflow.component.llm.FieldInfo {

    /**
     * Positional constructor: FieldInfo(fieldName, description, required).
     */
    public FieldInfo(String fieldName, String description, boolean required) {
        super();
        setFieldName(fieldName);
        setDescription(description);
        setRequired(required);
    }

    public FieldInfo() {
        super();
    }
}
