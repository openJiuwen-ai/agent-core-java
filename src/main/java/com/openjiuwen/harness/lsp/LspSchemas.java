/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import java.util.Objects;

/**
 * LSP Tool input/output schemas.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.lsp_tool._schemas}.</p>
 */
public class LspSchemas {

    /**
     * LSP operation types exposed to AI agents.
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
            for (LspOperation op : values()) {
                if (op.value.equals(value)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown LspOperation: " + value);
        }
    }

    /**
     * Input for goToDefinition operation.
     */
    public static class GoToDefinitionInput {
        private final LspOperation operation = LspOperation.GO_TO_DEFINITION;
        private String filePath;
        private int line;
        private int character = 1;

        public LspOperation getOperation() {
            return operation;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            if (line < 1) {
                throw new IllegalArgumentException("Line must be >= 1");
            }
            this.line = line;
        }

        public int getCharacter() {
            return character;
        }

        public void setCharacter(int character) {
            if (character < 1) {
                throw new IllegalArgumentException("Character must be >= 1");
            }
            this.character = character;
        }
    }

    /**
     * Input for findReferences operation.
     */
    public static class FindReferencesInput {
        private final LspOperation operation = LspOperation.FIND_REFERENCES;
        private String filePath;
        private int line;
        private int character = 1;
        private boolean includeDeclaration = true;

        public LspOperation getOperation() {
            return operation;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            if (line < 1) {
                throw new IllegalArgumentException("Line must be >= 1");
            }
            this.line = line;
        }

        public int getCharacter() {
            return character;
        }

        public void setCharacter(int character) {
            if (character < 1) {
                throw new IllegalArgumentException("Character must be >= 1");
            }
            this.character = character;
        }

        public boolean isIncludeDeclaration() {
            return includeDeclaration;
        }

        public void setIncludeDeclaration(boolean includeDeclaration) {
            this.includeDeclaration = includeDeclaration;
        }
    }

    /**
     * Input for documentSymbol operation.
     */
    public static class DocumentSymbolInput {
        private final LspOperation operation = LspOperation.DOCUMENT_SYMBOL;
        private String filePath;

        public LspOperation getOperation() {
            return operation;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }

    /**
     * Input for workspaceSymbol operation.
     */
    public static class WorkspaceSymbolInput {
        private final LspOperation operation = LspOperation.WORKSPACE_SYMBOL;
        private String filePath = "";
        private String query = "";

        public LspOperation getOperation() {
            return operation;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath != null ? filePath : "";
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query != null ? query : "";
        }
    }

    /**
     * Input for goToImplementation operation.
     */
    public static class GoToImplementationInput {
        private final LspOperation operation = LspOperation.GO_TO_IMPLEMENTATION;
        private String filePath;
        private int line;
        private int character = 1;

        public LspOperation getOperation() {
            return operation;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            if (line < 1) {
                throw new IllegalArgumentException("Line must be >= 1");
            }
            this.line = line;
        }

        public int getCharacter() {
            return character;
        }

        public void setCharacter(int character) {
            if (character < 1) {
                throw new IllegalArgumentException("Character must be >= 1");
            }
            this.character = character;
        }
    }

    /**
     * Input for prepareCallHierarchy operation.
     */
    public static class PrepareCallHierarchyInput {
        private final LspOperation operation = LspOperation.PREPARE_CALL_HIERARCHY;
        private String filePath;
        private int line;
        private int character = 1;

        public LspOperation getOperation() {
            return operation;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            if (line < 1) {
                throw new IllegalArgumentException("Line must be >= 1");
            }
            this.line = line;
        }

        public int getCharacter() {
            return character;
        }

        public void setCharacter(int character) {
            if (character < 1) {
                throw new IllegalArgumentException("Character must be >= 1");
            }
            this.character = character;
        }
    }

    /**
     * Input for incomingCalls operation.
     */
    public static class IncomingCallsInput {
        private final LspOperation operation = LspOperation.INCOMING_CALLS;
        private String filePath;
        private int line;
        private int character = 1;

        public LspOperation getOperation() {
            return operation;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            if (line < 1) {
                throw new IllegalArgumentException("Line must be >= 1");
            }
            this.line = line;
        }

        public int getCharacter() {
            return character;
        }

        public void setCharacter(int character) {
            if (character < 1) {
                throw new IllegalArgumentException("Character must be >= 1");
            }
            this.character = character;
        }
    }

    /**
     * Input for outgoingCalls operation.
     */
    public static class OutgoingCallsInput {
        private final LspOperation operation = LspOperation.OUTGOING_CALLS;
        private String filePath;
        private int line;
        private int character = 1;

        public LspOperation getOperation() {
            return operation;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            if (line < 1) {
                throw new IllegalArgumentException("Line must be >= 1");
            }
            this.line = line;
        }

        public int getCharacter() {
            return character;
        }

        public void setCharacter(int character) {
            if (character < 1) {
                throw new IllegalArgumentException("Character must be >= 1");
            }
            this.character = character;
        }
    }

    /**
     * LSP Tool output.
     */
    public static class LspToolOutput {
        private LspOperation operation;
        private String result;
        private String filePath;
        private Integer resultCount;
        private String error;

        public LspOperation getOperation() {
            return operation;
        }

        public void setOperation(LspOperation operation) {
            this.operation = operation;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public Integer getResultCount() {
            return resultCount;
        }

        public void setResultCount(Integer resultCount) {
            this.resultCount = resultCount;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}