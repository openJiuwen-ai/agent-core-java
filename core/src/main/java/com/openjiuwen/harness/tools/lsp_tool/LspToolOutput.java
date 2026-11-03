/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp_tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors Python's {@code LspToolOutput} in
 * {@code openjiuwen/harness/tools/lsp_tool/_schemas.py}.
 */
public class LspToolOutput {

    private LspOperation operation;
    private String result;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("result_count")
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
