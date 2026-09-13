/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;

/**
 * Abstract storage for saving/recovering session state.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.base.Storage}.
 * 
 * @since 0.1.7
 */
public abstract class Storage {
    /**
     * save.
     * 
     * @param session session
     * @since 0.1.7
     */
    public abstract void save(BaseSession session);

    /**
     * Recover the session state.
     * 
     * @param session the session to recover into
     * @param inputs optional interactive input for resumed execution
     * @since 0.1.7
     */
    public abstract void recover(BaseSession session, InteractiveInput inputs);

    /**
     * Recover session state without interactive input.
     * 
     * @param session session
     * @since 0.1.7
     */
    public void recover(BaseSession session) {
        recover(session, null);
    }

    /**
     * Clear stored state for the given ID.
     * 
     * @param id the session/workflow/agent ID
     * @since 0.1.7
     */
    public abstract void clear(String id);

    /**
     * Check if state exists for the given session.
     * 
     * @param session the session
     * @return true if state isExists
     * @since 0.1.7
     */
    public abstract boolean isExists(BaseSession session);
}
