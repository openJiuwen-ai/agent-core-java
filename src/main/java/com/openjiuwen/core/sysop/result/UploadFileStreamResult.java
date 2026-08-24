/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code UploadFileStreamResult} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
public class UploadFileStreamResult extends BaseResult<UploadFileChunkData> {

    public UploadFileStreamResult() {
    }

    public UploadFileStreamResult(int code, String message, UploadFileChunkData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
