// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for upload file stream operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.UploadFileStreamResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class UploadFileStreamResult extends BaseResult<UploadFileChunkData> {

    public UploadFileStreamResult(int code, String message, UploadFileChunkData data) {
        super(code, message, data);
    }

    public static UploadFileStreamResult success(UploadFileChunkData data) {
        return new UploadFileStreamResult(0, "success", data);
    }

    public static UploadFileStreamResult failure(int code, String message) {
        return new UploadFileStreamResult(code, message, null);
    }
}

