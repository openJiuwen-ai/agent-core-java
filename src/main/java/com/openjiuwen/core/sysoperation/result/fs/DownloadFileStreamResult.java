// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for download file stream operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.DownloadFileStreamResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class DownloadFileStreamResult extends BaseResult<DownloadFileChunkData> {

    public DownloadFileStreamResult(int code, String message, DownloadFileChunkData data) {
        super(code, message, data);
    }

    public static DownloadFileStreamResult success(DownloadFileChunkData data) {
        return new DownloadFileStreamResult(0, "success", data);
    }

    public static DownloadFileStreamResult failure(int code, String message) {
        return new DownloadFileStreamResult(code, message, null);
    }
}

