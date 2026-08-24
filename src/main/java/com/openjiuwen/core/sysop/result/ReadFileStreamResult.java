/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code ReadFileStreamResult} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
public class ReadFileStreamResult extends BaseResult<ReadFileChunkData> {

    public ReadFileStreamResult() {
    }

    public ReadFileStreamResult(int code, String message, ReadFileChunkData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
