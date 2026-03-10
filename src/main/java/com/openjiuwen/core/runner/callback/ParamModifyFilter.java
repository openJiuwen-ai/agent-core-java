// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.runner.callback;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Filter for modifying callback arguments.
 * <p>
 * Applies a modifier function to transform arguments before callback execution.
 * The modifier receives (args, kwargs) and returns a two-element Object array: [newArgs, newKwargs].
 */
public class ParamModifyFilter extends EventFilter {

    /**
     * Modifier that takes (args, kwargs) and returns a two-element array: [newArgs, newKwargs].
     * Element [0] should be Object[] (new args), element [1] should be Map&lt;String, Object&gt; (new kwargs).
     */
    private final BiFunction<Object[], Map<String, Object>, Object[]> modifier;

    public ParamModifyFilter(BiFunction<Object[], Map<String, Object>, Object[]> modifier) {
        this(modifier, "ParamModify");
    }

    public ParamModifyFilter(BiFunction<Object[], Map<String, Object>, Object[]> modifier, String name) {
        super(name);
        this.modifier = modifier;
    }

    @SuppressWarnings("unchecked")
    @Override
    public FilterResult filter(String event, CallbackInfo callback,
                                Object[] args, Map<String, Object> kwargs) {
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
