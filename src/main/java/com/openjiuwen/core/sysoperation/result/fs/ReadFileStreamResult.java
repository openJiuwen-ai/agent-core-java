// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for read file stream operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.ReadFileStreamResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ReadFileStreamResult extends BaseResult<ReadFileChunkData> {

    public ReadFileStreamResult(int code, String message, ReadFileChunkData data) {
        super(code, message, data);
    }

    public static ReadFileStreamResult success(ReadFileChunkData data) {
        return new ReadFileStreamResult(0, "success", data);
    }

    public static ReadFileStreamResult failure(int code, String message) {
        return new ReadFileStreamResult(code, message, null);
    }
}

