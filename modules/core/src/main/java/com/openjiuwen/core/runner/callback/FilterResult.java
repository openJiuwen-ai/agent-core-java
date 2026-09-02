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
 * Result returned by event filters.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterResult {
    private FilterAction action;

    /** New positional arguments if action is MODIFY. */
    private Object[] modifiedArgs;

    /** New keyword arguments if action is MODIFY. */
    private Map<String, Object> modifiedKwargs;

    /** Optional reason for the action taken. */
    private String reason;

    /**
     * Create a CONTINUE result.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static FilterResult continueResult() {
        return FilterResult.builder().action(FilterAction.CONTINUE).build();
    }

    /**
     * Create a CONTINUE result with modified arguments.
     * 
     * @param args args
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    public static FilterResult continueResult(Object[] args, Map<String, Object> kwargs) {
        return FilterResult.builder().action(FilterAction.CONTINUE).modifiedArgs(args).modifiedKwargs(kwargs).build();
    }

    /**
     * Create a SKIP result with reason.
     * 
     * @param reason reason
     * @return the result
     * @since 0.1.7
     */
    public static FilterResult skipResult(String reason) {
        return FilterResult.builder().action(FilterAction.SKIP).reason(reason).build();
    }

    /**
     * Create a STOP result with reason.
     * 
     * @param reason reason
     * @return the result
     * @since 0.1.7
     */
    public static FilterResult stopResult(String reason) {
        return FilterResult.builder().action(FilterAction.STOP).reason(reason).build();
    }

    /**
     * Create a MODIFY result with new arguments.
     * 
     * @param modifiedArgs modifiedArgs
     * @param modifiedKwargs modifiedKwargs
     * @return the result
     * @since 0.1.7
     */
    public static FilterResult modifyResult(Object[] modifiedArgs, Map<String, Object> modifiedKwargs) {
        return FilterResult.builder().action(FilterAction.MODIFY).modifiedArgs(modifiedArgs)
                .modifiedKwargs(modifiedKwargs).build();
    }
}
