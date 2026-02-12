// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for search files operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.SearchFilesResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class SearchFilesResult extends BaseResult<SearchFilesData> {

    public SearchFilesResult(int code, String message, SearchFilesData data) {
        super(code, message, data);
    }

    public static SearchFilesResult success(SearchFilesData data) {
        return new SearchFilesResult(0, "success", data);
    }

    public static SearchFilesResult failure(int code, String message) {
        return new SearchFilesResult(code, message, null);
    }
}

