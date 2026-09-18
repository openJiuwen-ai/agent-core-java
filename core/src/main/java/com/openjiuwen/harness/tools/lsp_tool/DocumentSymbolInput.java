/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp_tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors Python's {@code DocumentSymbolInput} in
 * {@code openjiuwen/harness/tools/lsp_tool/_schemas.py}.
 */
public class DocumentSymbolInput {

    private String operation = "documentSymbol";

    @JsonProperty("file_path")
    private String filePath;

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation == null ? "documentSymbol" : operation;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
