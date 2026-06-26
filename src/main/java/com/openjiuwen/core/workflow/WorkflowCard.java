/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.Map;

/**
 * Mirrors Python's {@code WorkflowCard} in
 * {@code openjiuwen/core/workflow/base.py}.
 */
public class WorkflowCard extends BaseCard {

    private String version = "";
    private Object inputParams;

    public WorkflowCard() {
        super();
    }

    public WorkflowCard(String name, String description) {
        super(null, name, description);
    }

    public WorkflowCard(String id, String name, String description, String version, Object inputParams) {
        super(id, name, description);
        this.version = version == null ? "" : version;
        this.inputParams = inputParams;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null ? "" : version;
    }

    public Object getInputParams() {
        return inputParams;
    }

    public void setInputParams(Object inputParams) {
        this.inputParams = inputParams;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolInfo toolInfo() {
        Map<String, Object> parameters = inputParams instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        return ToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(parameters)
                .build();
    }
}
