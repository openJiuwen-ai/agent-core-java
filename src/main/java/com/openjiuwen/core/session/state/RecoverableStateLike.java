/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.Map;

/**
 * Interface for recoverable state operations.
 * 
 * <p>Provides methods for getting and setting the entire state,
 * typically used for checkpointing and recovery.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface RecoverableStateLike {
    
    /**
     * Gets the entire state as a map.
     * 
     * @return a copy of the internal state
     */
    Map<String, Object> getState();
    
    /**
     * Sets the entire state from a map.
     * 
     * @param state the new state to set
     */
    void setState(Map<String, Object> state);
}

