/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp;

/**
 * LspInputs.
 * 
 * @since 0.1.7
 */
public final class LspInputs {
    /**
     * LspInputs.
     * 
     * @since 0.1.7
     */
    private LspInputs() {
    }

    /**
     * Public record GoToDefinitionInput used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record GoToDefinitionInput(String filePath, int line, int character, LspOperation operation) {
        public GoToDefinitionInput(String filePath, int line, int character) {
            this(filePath, line, character, LspOperation.GO_TO_DEFINITION);
        }
    }

    /**
     * Public record FindReferencesInput used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record FindReferencesInput(String filePath, int line, int character, boolean isDeclarationIncluded,
            LspOperation operation) {
        public FindReferencesInput(String filePath, int line, int character) {
            this(filePath, line, character, true, LspOperation.FIND_REFERENCES);
        }

        public boolean includeDeclaration() {
            return isDeclarationIncluded;
        }
    }

    /**
     * Public record DocumentSymbolInput used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record DocumentSymbolInput(String filePath, LspOperation operation) {
        public DocumentSymbolInput(String filePath) {
            this(filePath, LspOperation.DOCUMENT_SYMBOL);
        }
    }

    /**
     * Public record WorkspaceSymbolInput used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record WorkspaceSymbolInput(String query, LspOperation operation) {
        public WorkspaceSymbolInput(String query) {
            this(query, LspOperation.WORKSPACE_SYMBOL);
        }
    }

    /**
     * Public record PrepareCallHierarchyInput used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record PrepareCallHierarchyInput(String filePath, int line, int character, LspOperation operation) {
        public PrepareCallHierarchyInput(String filePath, int line, int character) {
            this(filePath, line, character, LspOperation.PREPARE_CALL_HIERARCHY);
        }
    }

    /**
     * Public record IncomingCallsInput used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record IncomingCallsInput(String filePath, int line, int character, LspOperation operation) {
        public IncomingCallsInput(String filePath, int line, int character) {
            this(filePath, line, character, LspOperation.INCOMING_CALLS);
        }
    }

    /**
     * Public record OutgoingCallsInput used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record OutgoingCallsInput(String filePath, int line, int character, LspOperation operation) {
        public OutgoingCallsInput(String filePath, int line, int character) {
            this(filePath, line, character, LspOperation.OUTGOING_CALLS);
        }
    }
}
