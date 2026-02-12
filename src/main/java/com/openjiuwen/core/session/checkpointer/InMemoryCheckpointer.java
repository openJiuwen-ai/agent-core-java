/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.graph.store.InMemoryStore;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.WorkflowSession;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of Checkpointer.
 * 
 * <p>Stores session state in memory using ConcurrentHashMaps.
 * State is not persisted to disk.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/checkpointer/checkpointer.py - InMemoryCheckpointer
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class InMemoryCheckpointer implements Checkpointer {
    
    private static final LoggerProtocol logger = LogManager.getLogger("session");
    
    /**
     * Task status interrupt key (from pregel module).
     */
    public static final String TASK_STATUS_INTERRUPT = "__interrupt__";
    
    /**
     * Map of session ID to agent storage.
     */
    private final Map<String, AgentStorage> agentStores = new ConcurrentHashMap<>();
    
    /**
     * Map of session ID to workflow storage.
     */
    private final Map<String, WorkflowStorage> workflowStores = new ConcurrentHashMap<>();
    
    /**
     * Map of session ID to set of workflow IDs.
     */
    private final Map<String, Set<String>> sessionToWorkflowIds = new ConcurrentHashMap<>();
    
    /**
     * Graph store for persisting graph state.
     */
    private final InMemoryStore graphStore;
    
    /**
     * Creates a new InMemoryCheckpointer.
     */
    public InMemoryCheckpointer() {
        this.graphStore = new InMemoryStore();
    }
    
    @Override
    public CompletableFuture<Void> preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
        return CompletableFuture.supplyAsync(() -> {
            String sessionId = session.getSessionId();
            String workflowId = getWorkflowId(session);
            
            logger.info("workflow: {} create or restore checkpoint from session: {}", 
                       workflowId, sessionId);
            
            WorkflowStorage workflowStore = workflowStores.computeIfAbsent(sessionId, 
                k -> new WorkflowStorage());
            sessionToWorkflowIds.computeIfAbsent(sessionId, k -> new HashSet<>());
            
            if (inputs != null) {
                // Recovery mode with interactive input
                workflowStore.recover(session, inputs);
            } else {
                // New workflow or check for conflict
                if (!workflowStore.exists(session)) {
                    return null;
                }
                
                Object forceDelEnv = session.getConfig().getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, false);
                boolean forceDel = Boolean.TRUE.equals(forceDelEnv);
                
                if (forceDel) {
                    try {
                        graphStore.delete(sessionId, workflowId).get();
                    } catch (Exception e) {
                        // Ignore delete errors
                    }
                    workflowStore.clear(workflowId);
                } else {
                    throw new JiuWenBaseException(
                        StatusCode.WORKFLOW_STATE_INVALID.getCode(),
                        StatusCode.WORKFLOW_STATE_INVALID.formatMessage(
                            Map.of("error_msg", "workflow state exists but non-interactive input and cleanup is disabled")
                        )
                    );
                }
            }
            return null;
        });
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> postWorkflowExecute(BaseSession session, Object result, 
                                                        Exception exception) {
        return CompletableFuture.supplyAsync(() -> {
            String sessionId = session.getSessionId();
            String workflowId = getWorkflowId(session);
            
            WorkflowStorage workflowStore = workflowStores.get(sessionId);
            Set<String> workflowIds = sessionToWorkflowIds.get(sessionId);
            
            if (exception != null) {
                logger.info("exception in workflow, save checkpoint for workflow: {} in session: {}",
                           workflowId, sessionId);
                if (workflowStore == null) {
                    throw new JiuWenBaseException(
                        StatusCode.SESSION_CHECKPOINTER_NONE_WORKFLOW_STORE_ERROR.getCode(),
                        StatusCode.SESSION_CHECKPOINTER_NONE_WORKFLOW_STORE_ERROR.getMessage()
                    );
                }
                workflowStore.save(session);
                if (workflowIds != null) {
                    workflowIds.add(workflowId);
                }
                throw new RuntimeException(exception);
            }
            
            // Check for interrupt in result
            Map<String, Object> resultMap = result instanceof Map ? (Map<String, Object>) result : null;
            boolean hasInterrupt = resultMap != null && resultMap.get(TASK_STATUS_INTERRUPT) != null;
            
            if (!hasInterrupt) {
                logger.info("clear checkpoint for workflow: {} in session: {}", workflowId, sessionId);
                try {
                    graphStore.delete(sessionId, workflowId).get();
                } catch (Exception e) {
                    // Ignore delete errors
                }
                
                if (workflowStore != null) {
                    workflowStore.clear(workflowId);
                    if (workflowIds != null) {
                        workflowIds.remove(workflowId);
                    }
                } else {
                    logger.warning("workflow_store of workflow: {} does not exist in session: {}",
                                  workflowId, sessionId);
                }
                
                // Clear session if not under agent session
                BaseSession parent = getParent(session);
                if (!(parent instanceof AgentSession)) {
                    logger.info("clear session: {}", sessionId);
                    workflowStores.remove(sessionId);
                    sessionToWorkflowIds.remove(sessionId);
                }
            } else {
                logger.info("interaction required, save checkpoint for workflow: {} in session: {}",
                           workflowId, sessionId);
                if (workflowStore == null) {
                    throw new JiuWenBaseException(
                        StatusCode.SESSION_CHECKPOINTER_NONE_WORKFLOW_STORE_ERROR.getCode(),
                        StatusCode.SESSION_CHECKPOINTER_NONE_WORKFLOW_STORE_ERROR.getMessage()
                    );
                }
                workflowStore.save(session);
                if (workflowIds != null) {
                    workflowIds.add(workflowId);
                }
            }
            return null;
        });
    }
    
    @Override
    public CompletableFuture<Void> preAgentExecute(BaseSession session, Object inputs) {
        return CompletableFuture.supplyAsync(() -> {
            String sessionId = session.getSessionId();
            String agentId = getAgentId(session);
            
            logger.info("agent: {} create or restore checkpoint from session: {}", agentId, sessionId);
            
            AgentStorage agentStore = agentStores.computeIfAbsent(sessionId, k -> new AgentStorage());
            agentStore.recover(session);
            
            if (inputs != null) {
                session.getState().setState(Map.of(Constant.INTERACTIVE_INPUT, java.util.List.of(inputs)));
            }
            
            return null;
        });
    }
    
    @Override
    public CompletableFuture<Void> interruptAgentExecute(BaseSession session) {
        return CompletableFuture.supplyAsync(() -> {
            String sessionId = session.getSessionId();
            String agentId = getAgentId(session);
            
            logger.info("interaction required, save checkpoint for agent: {} in session: {}",
                       agentId, sessionId);
            
            AgentStorage agentStore = agentStores.get(sessionId);
            if (agentStore == null) {
                throw new JiuWenBaseException(
                    StatusCode.SESSION_CHECKPOINTER_NONE_AGENT_STORE_ERROR.getCode(),
                    StatusCode.SESSION_CHECKPOINTER_NONE_AGENT_STORE_ERROR.getMessage()
                );
            }
            agentStore.save(session);
            
            return null;
        });
    }
    
    @Override
    public CompletableFuture<Void> postAgentExecute(BaseSession session) {
        return CompletableFuture.supplyAsync(() -> {
            String sessionId = session.getSessionId();
            String agentId = getAgentId(session);
            
            logger.info("agent finished, save checkpoint for agent: {} in session: {}", 
                       agentId, sessionId);
            
            AgentStorage agentStore = agentStores.get(sessionId);
            if (agentStore == null) {
                throw new JiuWenBaseException(
                    StatusCode.SESSION_CHECKPOINTER_NONE_AGENT_STORE_ERROR.getCode(),
                    StatusCode.SESSION_CHECKPOINTER_NONE_AGENT_STORE_ERROR.getMessage()
                );
            }
            agentStore.save(session);
            
            return null;
        });
    }
    
    @Override
    public CompletableFuture<Void> release(String sessionId) {
        return release(sessionId, null);
    }
    
    @Override
    public CompletableFuture<Void> release(String sessionId, String agentId) {
        return CompletableFuture.supplyAsync(() -> {
            if (agentId != null) {
                logger.info("clear checkpoint for agent: {} in session: {}", agentId, sessionId);
                AgentStorage agentStore = agentStores.get(sessionId);
                if (agentStore == null) {
                    logger.warning("agent_store of agent: {} does not exist in session: {}", 
                                  agentId, sessionId);
                    return null;
                }
                agentStore.clear(agentId);
            } else {
                logger.info("clear session: {}", sessionId);
                Set<String> workflowIds = sessionToWorkflowIds.get(sessionId);
                if (workflowIds != null) {
                    for (String workflowId : workflowIds) {
                        try {
                            graphStore.delete(sessionId, workflowId).get();
                        } catch (Exception e) {
                            // Ignore delete errors
                        }
                    }
                }
                sessionToWorkflowIds.remove(sessionId);
                workflowStores.remove(sessionId);
                agentStores.remove(sessionId);
            }
            return null;
        });
    }
    
    @Override
    public Store graphStore() {
        return graphStore;
    }
    
    /**
     * Gets the workflow ID from a session.
     *
     * @param session the base session
     * @return the workflow ID, or empty string if not available
     */
    private String getWorkflowId(BaseSession session) {
        if (session instanceof WorkflowSession workflowSession) {
            return workflowSession.getWorkflowId();
        }
        return "";
    }
    
    /**
     * Gets the agent ID from a session.
     *
     * @param session the base session
     * @return the agent ID, or null if not available
     */
    private String getAgentId(BaseSession session) {
        if (session instanceof AgentSession agentSession) {
            return agentSession.getAgentId();
        }
        return null;
    }
    
    /**
     * Gets the parent session from a session.
     *
     * @param session the base session
     * @return the parent session, or null if not available
     */
    private BaseSession getParent(BaseSession session) {
        if (session instanceof WorkflowSession workflowSession) {
            return workflowSession.getParent();
        }
        return null;
    }
}
