/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Mirrors Python's {@code ParamModifyFilter} in
 * {@code openjiuwen/core/runner/callback/filters.py}.
 */
public class ParamModifyFilter extends EventFilter {

    private final ArgumentsModifier modifier;

    public ParamModifyFilter(ArgumentsModifier modifier) {
        this(modifier, "ParamModify");
    }

    public ParamModifyFilter(ArgumentsModifier modifier, String name) {
        super(name);
        this.modifier = Objects.requireNonNull(modifier, "modifier");
    }

    @Override
    public FilterResult filter(
            String event,
            Function<Map<String, Object>, Object> callback,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        try {
            Modification modification = modifier.modify(safeArgs(args), safeKwargs(kwargs));
            return FilterResult.modifyResult(modification.args(), modification.kwargs());
        } catch (Exception error) {
            return FilterResult.skipResult("Parameter modification failed: " + error.getMessage());
        }
    }

    /**
     * Mirrors Python's modifier callable shape for
     * {@code openjiuwen/core/runner/callback/filters.py}.
     */
    @FunctionalInterface
    public interface ArgumentsModifier {

        Modification modify(Object[] args, Map<String, Object> kwargs) throws Exception;
    }

    /**
     * Mirrors Python's {@code (new_args, new_kwargs)} return pair in
     * {@code openjiuwen/core/runner/callback/filters.py}.
     */
    public record Modification(Object[] args, Map<String, Object> kwargs) {
    }
}
