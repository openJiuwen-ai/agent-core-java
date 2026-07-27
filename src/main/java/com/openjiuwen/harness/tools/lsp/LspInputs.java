/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp;

/**
 * Auto-generated for codecheck compliance.
 */
public final class LspInputs {
    private LspInputs() {
    }

    /**
 * Public record GoToDefinitionInput used by the Java parity implementation.
 *
 * @since 1.0
 */
public record GoToDefinitionInput(String filePath, int line, int character, LspOperation operation) {
        /**
         * Auto-generated for codecheck compliance.
         */
        public GoToDefinitionInput(String filePath, int line, int character) {
            this(filePath, line, character, LspOperation.GO_TO_DEFINITION);
        }
    }

    /**
 * Public record FindReferencesInput used by the Java parity implementation.
 *
 * @since 1.0
 */
public record FindReferencesInput(
            String filePath,
            int line,
            int character,
            boolean isDeclarationIncluded,
            LspOperation operation) {
        /**
         * Auto-generated for codecheck compliance.
         */
        public FindReferencesInput(String filePath, int line, int character) {
            this(filePath, line, character, true, LspOperation.FIND_REFERENCES);
        }

        /**
         * Auto-generated for compatibility.
         */
        public boolean includeDeclaration() {
            return isDeclarationIncluded;
        }
    }

    /**
 * Public record DocumentSymbolInput used by the Java parity implementation.
 *
 * @since 1.0
 */
public record DocumentSymbolInput(String filePath, LspOperation operation) {
        /**
         * Auto-generated for codecheck compliance.
         */
        public DocumentSymbolInput(String filePath) {
            this(filePath, LspOperation.DOCUMENT_SYMBOL);
        }
    }

    /**
 * Public record WorkspaceSymbolInput used by the Java parity implementation.
 *
 * @since 1.0
 */
public record WorkspaceSymbolInput(String query, LspOperation operation) {
        /**
         * Auto-generated for codecheck compliance.
         */
        public WorkspaceSymbolInput(String query) {
            this(query, LspOperation.WORKSPACE_SYMBOL);
        }
    }

    /**
 * Public record PrepareCallHierarchyInput used by the Java parity implementation.
 *
 * @since 1.0
 */
public record PrepareCallHierarchyInput(String filePath, int line, int character, LspOperation operation) {
        /**
         * Auto-generated for codecheck compliance.
         */
        public PrepareCallHierarchyInput(String filePath, int line, int character) {
            this(filePath, line, character, LspOperation.PREPARE_CALL_HIERARCHY);
        }
    }

    /**
 * Public record IncomingCallsInput used by the Java parity implementation.
 *
 * @since 1.0
 */
public record IncomingCallsInput(String filePath, int line, int character, LspOperation operation) {
        /**
         * Auto-generated for codecheck compliance.
         */
        public IncomingCallsInput(String filePath, int line, int character) {
            this(filePath, line, character, LspOperation.INCOMING_CALLS);
        }
    }

    /**
 * Public record OutgoingCallsInput used by the Java parity implementation.
 *
 * @since 1.0
 */
public record OutgoingCallsInput(String filePath, int line, int character, LspOperation operation) {
        /**
         * Auto-generated for codecheck compliance.
         */
        public OutgoingCallsInput(String filePath, int line, int character) {
            this(filePath, line, character, LspOperation.OUTGOING_CALLS);
        }
    }
}
