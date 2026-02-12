/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.Optional;

/**
 * Interface for read-only state operations.
 * 
 * <p>Provides methods for retrieving values from state using various key formats.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface ReadableStateLike {
    
    /**
     * Gets a value from the state using the given key.
     * 
     * <p>Key can be:
     * <ul>
     *   <li>A simple string key: "key"</li>
     *   <li>A nested path: "a.b.c"</li>
     *   <li>A reference path: "${path}"</li>
     *   <li>A list schema: ["${a}", "${b}"]</li>
     *   <li>A map schema: {"x": "${a}", "y": "${b}"}</li>
     * </ul>
     * 
     * @param key the key or schema to use for retrieval
     * @return the value, or null if not found
     */
    Object get(Object key);
    
    /**
     * Gets a value from the state using a key with a nested prefix.
     * 
     * @param key the key or schema to use for retrieval
     * @param nestedPrefix the prefix path to navigate to first
     * @return the value, or null if not found
     */
    Object getByPrefix(Object key, String nestedPrefix);
}

