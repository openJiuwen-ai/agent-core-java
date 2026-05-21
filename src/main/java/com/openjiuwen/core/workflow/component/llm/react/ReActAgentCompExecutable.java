/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm.react;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * ReAct agent workflow component executable.
 * <p>
 * Wraps a {@link ReActAgent} for use in workflow execution, providing
 * invoke and stream methods that integrate with workflow session management.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.react.react_executable.ReActAgentCompExecutable}.
 */
public class ReActAgentCompExecutable extends ComponentExecutable {

    private final ReActAgentCompConfig config;
    private final ReActAgent reactAgent;

    /**
     * Construct a new ReActAgentCompExecutable with the given configuration.
     *
     * @param config the component configuration
     */
    public ReActAgentCompExecutable(ReActAgentCompConfig config) {
        this.config = config;
        // Create a ReActAgent instance with a workflow-specific card
        AgentCard card = AgentCard.builder()
                .id("react_agent_workflow_executable")
                .name("ReAct Agent Workflow Executable")
                .description("ReAct agent for workflow execution")
                .build();
        this.reactAgent = new ReActAgent(card);
        // Configure the agent with the provided config
        this.reactAgent.configure(config);
    }

    /**
     * Get the ability manager for adding tools/workflows/agents.
     * <p>
     * This provides a public interface to manage agent capabilities.
     *
     * @return the ability manager instance
     */
    public AbilityManager getAbilityManager() {
        return reactAgent.getAbilityManager();
    }

    /**
     * Get the underlying ReActAgent instance.
     *
     * @return the ReActAgent instance
     */
    public ReActAgent getReactAgent() {
        return reactAgent;
    }

    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        try {
            // Convert NodeSessionApi to AgentSessionApi if needed
            AgentSessionApi agentSession = convertToAgentSession(session);
            // Execute the ReAct agent with the provided inputs
            Object result = reactAgent.invoke(inputs, agentSession);
            return result;
        } catch (Exception e) {
            // Handle errors appropriately
            Map<String, Object> errorOutput = new HashMap<>();
            errorOutput.put("output", "Error in ReAct execution: " + e.getMessage());
            errorOutput.put("result_type", "error");
            return errorOutput;
        }
    }

    @Override
    public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
        try {
            // Convert NodeSessionApi to AgentSessionApi
            AgentSessionApi agentSession = convertToAgentSession(session);
            
            // Execute the ReAct agent with streaming
            Iterator<Object> streamIterator = reactAgent.stream(inputs, agentSession);
            
            // Transform and yield chunks
            return new ReActStreamIterator(streamIterator);
        } catch (Exception e) {
            // Handle errors appropriately
            Map<String, Object> errorPayload = new HashMap<>();
            errorPayload.put("output", "Error in ReAct streaming: " + e.getMessage());
            errorPayload.put("result_type", "error");
            
            Map<String, Object> errorOutput = new HashMap<>();
            errorOutput.put("type", "error");
            errorOutput.put("payload", errorPayload);
            
            return Collections.singleton(errorOutput).iterator();
        }
    }

    /**
     * Convert NodeSessionApi to AgentSessionApi for agent execution.
     *
     * @param session the workflow node session
     * @return an AgentSessionApi instance
     */
    private AgentSessionApi convertToAgentSession(NodeSessionApi session) {
        // Create agent session with session ID and card
        String sessionId = session.getSessionId();
        AgentSessionApi agentSession = new AgentSessionApi(sessionId, reactAgent.getCard());
        return agentSession;
    }

    /**
     * Iterator that transforms ReAct agent stream output to workflow-compatible format.
     */
    private static class ReActStreamIterator implements Iterator<Object> {
        private final Iterator<Object> sourceIterator;

        public ReActStreamIterator(Iterator<Object> sourceIterator) {
            this.sourceIterator = sourceIterator;
        }

        @Override
        public boolean hasNext() {
            return sourceIterator.hasNext();
        }

        @Override
        public Object next() {
            Object chunk = sourceIterator.next();
            // Transform OutputSchema chunks
            if (chunk instanceof OutputSchema) {
                OutputSchema outputSchema = (OutputSchema) chunk;
                // If type is llm_output and content exists, extract content
                if ("llm_output".equals(outputSchema.getType()) 
                        && outputSchema.getPayload() instanceof Map) {
                    Map<String, Object> payload = (Map<String, Object>) outputSchema.getPayload();
                    if (payload.containsKey("content")) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("output", payload.get("content"));
                        return result;
                    }
                }
                // Otherwise, return payload directly
                return outputSchema.getPayload();
            }
            return chunk;
        }
    }
}