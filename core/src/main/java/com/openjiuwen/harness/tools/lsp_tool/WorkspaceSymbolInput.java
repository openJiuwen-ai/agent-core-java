/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp_tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors Python's {@code WorkspaceSymbolInput} in
 * {@code openjiuwen/harness/tools/lsp_tool/_schemas.py}.
 */
public class WorkspaceSymbolInput {

    private String operation = "workspaceSymbol";

    @JsonProperty("file_path")
    private String filePath = "";

    private String query = "";

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation == null ? "workspaceSymbol" : operation;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath == null ? "" : filePath;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query == null ? "" : query;
    }
}
