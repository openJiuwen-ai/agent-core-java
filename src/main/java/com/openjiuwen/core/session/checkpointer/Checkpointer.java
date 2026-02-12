/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;

import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for checkpointing session state.
 * 
 * <p>Provides methods for saving and restoring session state during
 * workflow and agent execution.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/checkpointer/base.py - Checkpointer
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface Checkpointer {
    
    /**
     * Generates a thread ID from session ID and workflow ID.
     *
     * @param sessionId the session identifier
     * @param workflowId the workflow identifier
     * @return the thread ID in format "sessionId:workflowId"
     */
    static String getThreadId(String sessionId, String workflowId) {
        return sessionId + ":" + workflowId;
    }
    
    /**
     * Called before workflow execution starts.
     * 
     * <p>Creates or restores workflow checkpoint from session.
     *
     * @param session the base session
     * @param inputs the interactive input, or null for new workflow
     * @return a CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> preWorkflowExecute(BaseSession session, InteractiveInput inputs);
    
    /**
     * Called after workflow execution completes.
     * 
     * <p>Handles saving state on exception/interrupt or clearing state on success.
     *
     * @param session the base session
     * @param result the execution result
     * @param exception the exception if any, or null on success
     * @return a CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> postWorkflowExecute(BaseSession session, Object result, Exception exception);
    
    /**
     * Called before agent execution starts.
     * 
     * <p>Creates or restores agent checkpoint from session.
     *
     * @param session the base session
     * @param inputs the inputs for the agent
     * @return a CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> preAgentExecute(BaseSession session, Object inputs);
    
    /**
     * Called when agent execution is interrupted.
     * 
     * <p>Saves checkpoint for later resumption.
     *
     * @param session the base session
     * @return a CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> interruptAgentExecute(BaseSession session);
    
    /**
     * Called after agent execution completes.
     * 
     * <p>Saves the final agent state.
     *
     * @param session the base session
     * @return a CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> postAgentExecute(BaseSession session);
    
    /**
     * Releases checkpoint resources for a session.
     *
     * @param sessionId the session identifier
     * @return a CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> release(String sessionId);
    
    /**
     * Releases checkpoint resources for a specific agent in a session.
     *
     * @param sessionId the session identifier
     * @param agentId the agent identifier, or null to release entire session
     * @return a CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> release(String sessionId, String agentId);
    
    /**
     * Gets the graph store used for persisting graph state.
     *
     * @return the graph store
     */
    Store graphStore();
}
