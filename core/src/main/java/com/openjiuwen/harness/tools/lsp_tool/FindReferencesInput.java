/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp_tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors Python's {@code FindReferencesInput} in
 * {@code openjiuwen/harness/tools/lsp_tool/_schemas.py}.
 */
public class FindReferencesInput {

    private String operation = "findReferences";

    @JsonProperty("file_path")
    private String filePath;

    private int line;
    private int character = 1;

    @JsonProperty("include_declaration")
    private boolean includeDeclaration = true;

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation == null ? "findReferences" : operation;
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
        this.line = line;
    }

    public int getCharacter() {
        return character;
    }

    public void setCharacter(int character) {
        this.character = character;
    }

    public boolean isIncludeDeclaration() {
        return includeDeclaration;
    }

    public void setIncludeDeclaration(boolean includeDeclaration) {
        this.includeDeclaration = includeDeclaration;
    }
}
