/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp_tool;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors Python's {@code LspOperation} in
 * {@code openjiuwen/harness/tools/lsp_tool/_schemas.py}.
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

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
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
