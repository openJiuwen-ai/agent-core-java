/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Filter for modifying callback arguments.
 * <p>
 * Applies a modifier function to transform arguments before callback execution.
 * The modifier receives (args, kwargs) and returns a two-element Object array: [newArgs, newKwargs].
 * 
 * @since 0.1.7
 */
public class ParamModifyFilter extends EventFilter {
    private final BiFunction<Object[], Map<String, Object>, Object[]> modifier;

    /**
     * ParamModifyFilter.
     * 
     * @param modifier modifier
     * @since 0.1.7
     */
    public ParamModifyFilter(BiFunction<Object[], Map<String, Object>, Object[]> modifier) {
        this(modifier, "ParamModify");
    }

    /**
     * ParamModifyFilter.
     * 
     * @param modifier modifier
     * @param name name
     * @since 0.1.7
     */
    public ParamModifyFilter(BiFunction<Object[], Map<String, Object>, Object[]> modifier, String name) {
        super(name);
        this.modifier = modifier;
    }

    /**
     * filter.
     * 
     * @param event event
     * @param callback callback
     * @param args args
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    @Override
    public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs) {
        try {
            Object[] modified = modifier.apply(args, kwargs);
            Object[] newArgs = (Object[]) modified[0];
            Map<String, Object> newKwargs = (Map<String, Object>) modified[1];
            return FilterResult.modifyResult(newArgs, newKwargs);
        } catch (Exception e) {
            return FilterResult.skipResult("Parameter modification failed: " + e.getMessage());
        }
    }
}
