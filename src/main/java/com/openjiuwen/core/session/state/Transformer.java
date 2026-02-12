/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

/**
 * Functional interface for state transformation.
 * 
 * <p>Transforms a readable state into any type of result.
 * This interface is used for custom state querying and transformation logic.
 * 
 * <p>Example usage:
 * <pre>{@code
 * Transformer<Integer> sumTransformer = state -> {
 *     Integer a = (Integer) state.get("a");
 *     Integer b = (Integer) state.get("b");
 *     return (a != null ? a : 0) + (b != null ? b : 0);
 * };
 * int sum = state.getByTransformer(sumTransformer);
 * }</pre>
 * 
 * @param <T> the type of the transformation result
 * @author OpenJiuwen
 * @since 1.0.0
 */
@FunctionalInterface
public interface Transformer<T> {
    
    /**
     * Transforms the given state into a result.
     * 
     * @param state the readable state to transform
     * @return the transformation result
     */
    T transform(ReadableStateLike state);
}

