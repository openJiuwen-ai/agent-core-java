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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterResult {

    /** The action to take (CONTINUE, STOP, SKIP, MODIFY). */
    private FilterAction action;

    /** New positional arguments if action is MODIFY. */
    private Object[] modifiedArgs;

    /** New keyword arguments if action is MODIFY. */
    private Map<String, Object> modifiedKwargs;

    /** Optional reason for the action taken. */
    private String reason;

    public static FilterResultBuilder builder() {
        return new FilterResultBuilder();
    }

    /**
     * Create a CONTINUE result.
     */
    public static FilterResult continueResult() {
        return FilterResult.builder().action(FilterAction.CONTINUE).build();
    }

    /**
     * Create a CONTINUE result with modified arguments.
     */
    public static FilterResult continueResult(Object[] args, Map<String, Object> kwargs) {
        return FilterResult.builder()
                .action(FilterAction.CONTINUE)
                .modifiedArgs(args)
                .modifiedKwargs(kwargs)
                .build();
    }

    /**
     * Create a SKIP result with reason.
     */
    public static FilterResult skipResult(String reason) {
        return FilterResult.builder()
                .action(FilterAction.SKIP)
                .reason(reason)
                .build();
    }

    /**
     * Create a STOP result with reason.
     */
    public static FilterResult stopResult(String reason) {
        return FilterResult.builder()
                .action(FilterAction.STOP)
                .reason(reason)
                .build();
    }

    /**
     * Create a MODIFY result with new arguments.
     */
    public static FilterResult modifyResult(Object[] modifiedArgs, Map<String, Object> modifiedKwargs) {
        return FilterResult.builder()
                .action(FilterAction.MODIFY)
                .modifiedArgs(modifiedArgs)
                .modifiedKwargs(modifiedKwargs)
                .build();
    }

    public FilterAction getAction() {
        return action;
    }

    public void setAction(FilterAction action) {
        this.action = action;
    }

    public Object[] getModifiedArgs() {
        return modifiedArgs;
    }

    public void setModifiedArgs(Object[] modifiedArgs) {
        this.modifiedArgs = modifiedArgs;
    }

    public Map<String, Object> getModifiedKwargs() {
        return modifiedKwargs;
    }

    public void setModifiedKwargs(Map<String, Object> modifiedKwargs) {
        this.modifiedKwargs = modifiedKwargs;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public static final class FilterResultBuilder {
        private FilterAction action;
        private Object[] modifiedArgs;
        private Map<String, Object> modifiedKwargs;
        private String reason;

        public FilterResultBuilder action(FilterAction action) {
            this.action = action;
            return this;
        }

        public FilterResultBuilder modifiedArgs(Object[] modifiedArgs) {
            this.modifiedArgs = modifiedArgs;
            return this;
        }

        public FilterResultBuilder modifiedKwargs(Map<String, Object> modifiedKwargs) {
            this.modifiedKwargs = modifiedKwargs;
            return this;
        }

        public FilterResultBuilder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public FilterResult build() {
            FilterResult result = new FilterResult();
            result.setAction(action);
            result.setModifiedArgs(modifiedArgs);
            result.setModifiedKwargs(modifiedKwargs);
            result.setReason(reason);
            return result;
        }
    }
}
