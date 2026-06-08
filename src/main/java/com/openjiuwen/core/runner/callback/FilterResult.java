/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Mirrors Python's {@code FilterResult} in
 * {@code openjiuwen/core/runner/callback/models.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterResult {

    private FilterAction action;

    private Object[] modifiedArgs;

    private Map<String, Object> modifiedKwargs;

    private String reason;

    public static FilterResult continueResult() {
        return FilterResult.builder().action(FilterAction.CONTINUE).build();
    }

    public static FilterResult continueResult(Object[] args, Map<String, Object> kwargs) {
        return FilterResult.builder()
                .action(FilterAction.CONTINUE)
                .modifiedArgs(args)
                .modifiedKwargs(kwargs)
                .build();
    }

    public static FilterResult skipResult(String reason) {
        return FilterResult.builder()
                .action(FilterAction.SKIP)
                .reason(reason)
                .build();
    }

    public static FilterResult stopResult(String reason) {
        return FilterResult.builder()
                .action(FilterAction.STOP)
                .reason(reason)
                .build();
    }

    public static FilterResult modifyResult(Object[] modifiedArgs, Map<String, Object> modifiedKwargs) {
        return FilterResult.builder()
                .action(FilterAction.MODIFY)
                .modifiedArgs(modifiedArgs)
                .modifiedKwargs(modifiedKwargs)
                .build();
    }
}
