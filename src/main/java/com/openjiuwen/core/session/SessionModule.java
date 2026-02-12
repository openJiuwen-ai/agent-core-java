/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;

/**
 * Session module utilities and default instances.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public final class SessionModule {
    
    private static volatile Checkpointer defaultInMemoryCheckpointer;
    
    private SessionModule() {
        // Utility class
    }
    
    /**
     * Gets the default in-memory checkpointer.
     * 
     * @return the default checkpointer
     */
    public static synchronized Checkpointer getDefaultInMemoryCheckpointer() {
        if (defaultInMemoryCheckpointer == null) {
            defaultInMemoryCheckpointer = new InMemoryCheckpointer();
        }
        return defaultInMemoryCheckpointer;
    }
}

