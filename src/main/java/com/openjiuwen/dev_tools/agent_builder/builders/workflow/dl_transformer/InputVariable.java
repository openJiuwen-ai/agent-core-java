/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Input variable model.
 * <p>
 * Mirrors Python's {@code InputVariable} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/models.py}.
 */
public class InputVariable {
    private final String type;
    private final Object content;
    private final Map<String, Object> extra;
    private final Map<String, Object> schema;

    public InputVariable(String type, Object content, Map<String, Object> extra) {
        this(type, content, extra, null);
    }

    public InputVariable(String type, Object content, Map<String, Object> extra, Map<String, Object> schema) {
        this.type = type;
        this.content = content;
        this.extra = extra != null ? new LinkedHashMap<>(extra) : new LinkedHashMap<>();
        this.schema = schema != null ? new LinkedHashMap<>(schema) : null;
    }

    public String getType() {
        return type;
    }

    public Object getContent() {
        return content;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public Map<String, Object> getSchema() {
        return schema;
    }
}
