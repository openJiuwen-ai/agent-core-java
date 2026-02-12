/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

/**
 * Constants for state management.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public final class StateConstants {
    
    private StateConstants() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Key for IO state storage.
     */
    public static final String IO_STATE_KEY = "io_state";
    
    /**
     * Key for IO state updates storage.
     */
    public static final String IO_STATE_UPDATES_KEY = "io_state_updates";
    
    /**
     * Key for global state storage.
     */
    public static final String GLOBAL_STATE_KEY = "global_state";
    
    /**
     * Key for global state updates storage.
     */
    public static final String GLOBAL_STATE_UPDATES_KEY = "global_state_updates";
    
    /**
     * Key for component state storage.
     */
    public static final String COMP_STATE_KEY = "comp_state";
    
    /**
     * Key for workflow state storage.
     */
    public static final String WORKFLOW_STATE_KEY = "workflow_state";
    
    /**
     * Key for agent state storage.
     */
    public static final String AGENT_STATE_KEY = "agent_state";
    
    /**
     * Key for component state updates storage.
     */
    public static final String COMP_STATE_UPDATES_KEY = "comp_state_updates";
    
    /**
     * Key for workflow state updates storage.
     */
    public static final String WORKFLOW_STATE_UPDATES_KEY = "workflow_state_updates";
    
    /**
     * Default node identifier.
     */
    public static final String DEFAULT_NODE_ID = "default";
    
    /**
     * Default workflow identifier.
     */
    public static final String DEFAULT_WORKFLOW_ID = "workflow";
}

