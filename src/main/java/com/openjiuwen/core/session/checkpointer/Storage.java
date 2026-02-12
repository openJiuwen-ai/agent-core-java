/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;

/**
 * Abstract interface for session state storage.
 * 
 * <p>Provides methods for saving, recovering, and clearing session state.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/checkpointer/storage.py - Storage
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface Storage {
    
    /**
     * Saves the session state.
     *
     * @param session the base session containing state to save
     */
    void save(BaseSession session);
    
    /**
     * Recovers session state.
     *
     * @param session the base session to recover state into
     * @param inputs the interactive input, or null for basic recovery
     */
    void recover(BaseSession session, InteractiveInput inputs);
    
    /**
     * Recovers session state without interactive input.
     *
     * @param session the base session to recover state into
     */
    default void recover(BaseSession session) {
        recover(session, null);
    }
    
    /**
     * Clears stored state for the given identifier.
     *
     * @param id the identifier (agent ID or workflow ID)
     */
    void clear(String id);
    
    /**
     * Checks if state exists for the given session.
     *
     * @param session the base session to check
     * @return true if state exists, false otherwise
     */
    boolean exists(BaseSession session);
}
