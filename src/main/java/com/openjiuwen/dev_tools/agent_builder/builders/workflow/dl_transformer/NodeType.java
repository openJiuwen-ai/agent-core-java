/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

/**
 * Node type enumeration.
 * <p>
 * Mirrors Python's {@code NodeType} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/models.py}.
 */
public enum NodeType {
    Start("Start", "1"),
    End("End", "2"),
    LLM("LLM", "3"),
    IntentDetection("IntentDetection", "6"),
    Questioner("Questioner", "7"),
    Code("Code", "10"),
    Plugin("Plugin", "19"),
    Output("Output", "9"),
    Branch("Branch", "4");

    private final String dlType;
    private final String dslType;

    NodeType(String dlType, String dslType) {
        this.dlType = dlType;
        this.dslType = dslType;
    }

    public String getDlType() { return dlType; }
    public String getDslType() { return dslType; }

    /**
     * Find NodeType by DL type string.
     */
    public static NodeType fromDlType(String dlType) {
        for (NodeType type : values()) {
            if (type.dlType.equals(dlType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown node type: " + dlType);
    }
}
