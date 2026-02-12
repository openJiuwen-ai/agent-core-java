/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.Map;

/**
 * Interface for full state operations.
 * 
 * <p>Combines readable and recoverable state operations with update capability
 * and transformer-based querying.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface StateLike extends ReadableStateLike, RecoverableStateLike {
    
    /**
     * Updates the state with the given data.
     * 
     * <p>Keys in the update map can be nested paths (e.g., "a.b.c").
     * Values of null will delete the corresponding key.
     * 
     * @param data the update data
     */
    void update(Map<String, Object> data);
    
    /**
     * Gets a value using a transformer function.
     * 
     * @param <T> the type of the result
     * @param transformer the transformer to apply
     * @return the transformation result
     */
    <T> T getByTransformer(Transformer<T> transformer);
}

