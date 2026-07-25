/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Executable for ReActAgentComp workflow component.
 * <p>
 * Wraps a {@link ReActAgent} instance and provides invoke/stream capabilities
 * for use within a workflow graph. Collect and transform are not supported —
 * the component only supports batch-in/batch-out and batch-in/stream-out.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.react.ReActAgentCompExecutable}.
 *
 * @since 1.0.0
 */
public class ReActAgentCompExecutable extends ComponentExecutable {
    private final ReActAgentCompConfig config;
    private final ReActAgent reactAgent;

    /**
     * Create an executable from the given config.
     *
     * @param config component configuration
     */
    public ReActAgentCompExecutable(ReActAgentCompConfig config) {
        this.config = config;
        this.reactAgent = new ReActAgent(AgentCard.builder()
                .id("react_agent_workflow_executable")
                .name("ReAct Agent Workflow Executable")
                .description("ReAct agent for workflow execution")
                .build());
        this.reactAgent.configure(config);
    }

    /**
     * Get the ability manager for adding tools/workflows/agents.
     *
     * @return the ability manager instance
     */
    public AbilityManager getAbilityManager() {
        return reactAgent.getAbilityManager();
    }

    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        com.openjiuwen.core.session.AgentSessionApi agentSession = toAgentSession(session);
        Object result = reactAgent.invoke(inputs, agentSession);
        if (result instanceof Map<?, ?> map) {
            return map;
        }
        return result;
    }

    @Override
    public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
        com.openjiuwen.core.session.AgentSessionApi agentSession = toAgentSession(session);
        Iterator<Object> agentStream = reactAgent.stream(inputs, agentSession,
                List.of(StreamMode.OUTPUT));

        List<Object> results = new ArrayList<>();
        while (agentStream.hasNext()) {
            Object chunk = agentStream.next();
            Object processed = processStreamChunk(chunk);
            if (processed != null) {
                results.add(processed);
            }
        }
        return results.iterator();
    }

    @Override
    public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
        throw new UnsupportedOperationException(
                "Component 'ReActAgentCompExecutable' is missing required method: collect()");
    }

    @Override
    public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
        throw new UnsupportedOperationException(
                "Component 'ReActAgentCompExecutable' is missing required method: transform()");
    }

    private static com.openjiuwen.core.session.AgentSessionApi toAgentSession(NodeSessionApi nodeSessionApi) {
        if (nodeSessionApi == null) {
            return null;
        }
        if (nodeSessionApi instanceof com.openjiuwen.core.session.AgentSessionApi agentSessionApi) {
            return agentSessionApi;
        }
        return new NodeSessionApiAdapter(nodeSessionApi);
    }

    /**
     * Process a stream chunk from the ReActAgent.
     * <p>
     * If the chunk is an OutputSchema of type 'llm_output' with a 'content' key in its
     * payload, extract the content. Otherwise, use the payload directly.
     *
     * @param chunk the stream chunk to process
     * @return the processed chunk
     */
    private static Object processStreamChunk(Object chunk) {
        if (chunk instanceof OutputSchema outputSchema) {
            if ("llm_output".equals(outputSchema.getType())) {
                Object payload = outputSchema.getPayload();
                if (payload instanceof Map<?, ?> map && map.containsKey("content")) {
                    return Map.of("output", map.get("content"));
                }
            }
            return outputSchema.getPayload();
        }
        if (chunk instanceof Map<?, ?> map) {
            if ("llm_output".equals(map.get("type"))) {
                Object payload = map.get("payload");
                if (payload instanceof Map<?, ?> payloadMap && payloadMap.containsKey("content")) {
                    return Map.of("output", payloadMap.get("content"));
                }
            }
        }
        return chunk;
    }

    /**
     * Adapter that wraps a NodeSessionApi to implement AgentSessionApi.
     */
    private static final class NodeSessionApiAdapter implements AgentSessionApi {
        private final NodeSessionApi delegate;

        private NodeSessionApiAdapter(NodeSessionApi delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getSessionId() {
            return delegate.getSessionId();
        }

        @Override
        public Object getState(String key) {
            return delegate.getState(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            delegate.updateState(data);
        }

        @Override
        public void writeStream(Object data) {
            delegate.writeStream(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            StreamWriterManager mgr = delegate.streamWriterManager();
            if (mgr != null) {
                return mgr.streamIterator();
            }
            return List.<Object>of().iterator();
        }
    }
}
