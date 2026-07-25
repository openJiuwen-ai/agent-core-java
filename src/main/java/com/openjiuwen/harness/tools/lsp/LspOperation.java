/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp;

/**
 * Public enum LspOperation used by the Java parity implementation.
 *
 * @since 1.0
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public String value() {
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static LspOperation fromValue(String value) {
        for (LspOperation operation : values()) {
            if (operation.value.equals(value)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Unsupported LSP operation: " + value);
    }
}
