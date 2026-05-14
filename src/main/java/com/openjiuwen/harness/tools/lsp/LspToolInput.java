/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp;

/**
 * Minimal typed input projection for the unified Java harness LSP tool.
 *
 * <p>Mirrors Python's {@code LspToolInput} union in
 * {@code openjiuwen.harness.tools.lsp_tool._schemas}.
 */
public class LspToolInput {

    private LspOperation operation;
    private String filePath = "";
    private int line = 1;
    private int character = 1;
    private String query = "";
    private boolean includeDeclaration = true;
    private int limit = 50;

    public LspOperation getOperation() {
        return operation;
    }

    public void setOperation(LspOperation operation) {
        this.operation = operation;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath == null ? "" : filePath;
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

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query == null ? "" : query;
    }

    public boolean isIncludeDeclaration() {
        return includeDeclaration;
    }

    public void setIncludeDeclaration(boolean includeDeclaration) {
        this.includeDeclaration = includeDeclaration;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
