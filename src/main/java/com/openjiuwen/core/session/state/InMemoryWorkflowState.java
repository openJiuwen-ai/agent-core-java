/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

import java.util.HashMap;

/**
 * In-memory implementation of CommitState for workflow execution.
 * 
 * <p>Creates all required state stores as InMemoryCommitState instances.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class InMemoryWorkflowState extends CommitState {
    
    /**
     * Creates a new InMemoryWorkflowState with a new global state.
     */
    public InMemoryWorkflowState() {
        this(null);
    }
    
    /**
     * Creates a new InMemoryWorkflowState with the given global state.
     * 
     * @param globalState the global state to use, or null to create a new one
     */
    public InMemoryWorkflowState(CommitStateLike globalState) {
        super(
            new InMemoryCommitState(),
            globalState != null ? globalState : new InMemoryCommitState(),
            new InMemoryCommitState(),
            new InMemoryCommitState(),
            new HashMap<>(),
            "",
            StateConstants.DEFAULT_NODE_ID,
            globalState == null
        );
    }
}

