/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Backward-compatible read-file payload for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code ReadFileData} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.ReadFileData}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class ReadFileData {

    private String path;
    private Object content;
    private String mode;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
