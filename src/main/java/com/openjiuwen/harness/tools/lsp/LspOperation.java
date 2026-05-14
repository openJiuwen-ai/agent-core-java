/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp;

/**
 * LSP operation names exposed to Java harness agents.
 *
 * <p>Mirrors Python's {@code LspOperation} in
 * {@code openjiuwen.harness.tools.lsp_tool._schemas}.
 */
public enum LspOperation {
    GO_TO_DEFINITION("goToDefinition"),
    FIND_REFERENCES("findReferences"),
    DOCUMENT_SYMBOL("documentSymbol"),
    WORKSPACE_SYMBOL("workspaceSymbol"),
    GO_TO_IMPLEMENTATION("goToImplementation"),
    PREPARE_CALL_HIERARCHY("prepareCallHierarchy"),
    INCOMING_CALLS("incomingCalls"),
    OUTGOING_CALLS("outgoingCalls");

    private final String value;

    LspOperation(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LspOperation fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (LspOperation operation : values()) {
            if (operation.value.equals(value)) {
                return operation;
            }
        }
        return null;
    }
}
