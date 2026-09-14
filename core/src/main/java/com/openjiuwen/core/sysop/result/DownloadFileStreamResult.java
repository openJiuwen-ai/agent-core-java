/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code DownloadFileStreamResult} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
public class DownloadFileStreamResult extends BaseResult<DownloadFileChunkData> {

    public DownloadFileStreamResult() {
    }

    public DownloadFileStreamResult(int code, String message, DownloadFileChunkData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
