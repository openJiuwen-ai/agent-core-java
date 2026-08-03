/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import java.util.Map;

/**
 * Backward-compatible execute-code stream payload for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code ExecuteCodeChunkData} in
 * {@code openjiuwen/core/sys_operation/result/code_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.ExecuteCodeChunkData}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class ExecuteCodeChunkData {

    private String text = "";
    private String type;
    private int chunkIndex;
    private Integer exitCode;
    private Map<String, Object> metadata;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
