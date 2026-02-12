/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;

/**
 * Base utility class for session management.
 * 
 * <p>Provides a singleton default in-memory checkpointer for sessions
 * that don't have a custom checkpointer configured.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/base.py
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public final class SessionBase {
    
    /**
     * Singleton default in-memory checkpointer.
     */
    private static volatile Checkpointer defaultInMemoryCheckpointer = null;
    
    /**
     * Lock object for thread-safe initialization.
     */
    private static final Object LOCK = new Object();
    
    /**
     * Private constructor to prevent instantiation.
     */
    private SessionBase() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Gets the default in-memory checkpointer.
     * 
     * <p>This method uses lazy initialization with double-checked locking
     * to ensure thread-safe singleton creation.
     * 
     * @return the default in-memory checkpointer
     */
    public static Checkpointer getDefaultInMemoryCheckpointer() {
        if (defaultInMemoryCheckpointer == null) {
            synchronized (LOCK) {
                if (defaultInMemoryCheckpointer == null) {
                    defaultInMemoryCheckpointer = new InMemoryCheckpointer();
                }
            }
        }
        return defaultInMemoryCheckpointer;
    }
}

