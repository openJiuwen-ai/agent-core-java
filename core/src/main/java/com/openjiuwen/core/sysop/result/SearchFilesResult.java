/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code SearchFilesResult} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
public class SearchFilesResult extends BaseResult<SearchFilesData> {

    public SearchFilesResult() {
    }

    public SearchFilesResult(int code, String message, SearchFilesData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
