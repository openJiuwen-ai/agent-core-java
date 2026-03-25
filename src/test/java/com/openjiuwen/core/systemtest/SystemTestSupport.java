/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.application.llm.LlmAgent;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.multiagent.BaseGroup;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.session.tracer.Span;
import com.openjiuwen.core.session.tracer.TraceWorkflowSpan;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;

import org.junit.jupiter.api.AfterEach;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

abstract class SystemTestSupport {

    private final Set<String> sessionIds = new LinkedHashSet<>();
    private final Set<String> workflowIds = new LinkedHashSet<>();
    private final Set<String> agentIds = new LinkedHashSet<>();
    private final Set<String> groupIds = new LinkedHashSet<>();

    protected void assumeRemoteModelAvailable() {
        assumeTrue(isNotBlank(ApiConfigLoader.getApiBase()), "API_BASE is required");
        assumeTrue(isNotBlank(ApiConfigLoader.getApiKey()), "API_KEY is required");
        assumeTrue(isNotBlank(ApiConfigLoader.getModelProvider()), "MODEL_PROVIDER is required");
        assumeTrue(isNotBlank(ApiConfigLoader.getModelName()), "MODEL_NAME is required");
    }

    protected String trackSessionId(String prefix) {
        String sessionId = uniqueId(prefix);
        sessionIds.add(sessionId);
        return sessionId;
    }

    protected void registerWorkflow(Workflow workflow) {
        WorkflowCard card = workflow.getCard();
        Runner.resourceMgr().addWorkflow(card, () -> workflow, null);
        workflowIds.add(card.getId());
    }

    protected void registerAgent(BaseAgent agent) {
        Runner.resourceMgr().addAgent(agent.getCard(), () -> agent, null);
        agentIds.add(agent.getCard().getId());
    }

    protected void registerGroup(BaseGroup group) {
        Runner.resourceMgr().addAgentGroup(group.getCard(), () -> group, null);
        groupIds.add(group.getCard().getId());
    }

    protected String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected ModelClientConfig remoteClientConfig(double timeoutSeconds) {
        return ModelClientConfig.builder()
                .clientProvider(ApiConfigLoader.getModelProvider())
                .apiKey(ApiConfigLoader.getApiKey())
                .apiBase(ApiConfigLoader.getApiBase())
                .timeout(timeoutSeconds)
                .maxRetries(2)
                .verifySsl(ApiConfigLoader.getSslVerify())
                .build();
    }

    protected ModelRequestConfig remoteRequestConfig(double temperature, int maxTokens) {
        return ModelRequestConfig.builder()
                .modelName(ApiConfigLoader.getModelName())
                .temperature(temperature)
                .topP(0.9)
                .maxTokens(maxTokens)
                .build();
    }

    protected ModelConfig remoteApplicationModelConfig(double temperature) {
        BaseModelInfo modelInfo = BaseModelInfo.builder()
                .apiKey(ApiConfigLoader.getApiKey())
                .apiBase(ApiConfigLoader.getApiBase())
                .modelName(ApiConfigLoader.getModelName())
                .temperature(temperature)
                .topP(0.9)
                .timeout(60)
                .build();
        return new ModelConfig(ApiConfigLoader.getModelProvider(), modelInfo);
    }

    protected LlmAgent newRemoteLlmAgent(String agentId, String systemPrompt) {
        LlmAgentConfig config = LlmAgentConfig.builder()
                .id(agentId)
                .description("system test llm agent")
                .model(remoteApplicationModelConfig(0.1))
                .promptTemplate(systemPrompt == null ? List.of() : systemPrompt(systemPrompt))
                .build();
        return new LlmAgent(config);
    }

    protected ReActAgent newRemoteReActAgent(String agentId, String systemPrompt) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(agentId)
                .name(agentId)
                .description("system test react agent")
                .build());

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(systemPrompt == null ? List.of() : systemPrompt(systemPrompt))
                .maxIterations(3)
                .build()
                .configureModelClient(
                        ApiConfigLoader.getModelProvider(),
                        ApiConfigLoader.getApiKey(),
                        ApiConfigLoader.getApiBase(),
                        ApiConfigLoader.getModelName(),
                        ApiConfigLoader.getSslVerify()
                );

        config.getModelConfigObj().setTemperature(0.1);
        config.getModelConfigObj().setTopP(0.9);
        config.getModelConfigObj().setMaxTokens(256);
        agent.configure(config);
        return agent;
    }

    protected InvocationCapture invokeAgent(BaseAgent agent, Map<String, Object> inputs, String sessionId) {
        AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard());
        session.preRun(inputs);
        Object result;
        try {
            result = agent.invoke(inputs, session);
        } finally {
            session.postRun();
        }
        List<Object> streamItems = collect(session.streamIterator());
        return new InvocationCapture(result, streamItems, flattenText(List.of(result, streamItems)));
    }

    protected InvocationCapture streamAgent(BaseAgent agent, Map<String, Object> inputs, String sessionId) {
        AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard());
        session.preRun(inputs);
        List<Object> streamItems;
        try {
            streamItems = collect(agent.stream(inputs, session, List.of(StreamMode.OUTPUT)));
        } finally {
            session.postRun();
        }
        return new InvocationCapture(streamItems, streamItems, flattenText(streamItems));
    }

    protected List<Object> collect(Iterator<Object> iterator) {
        List<Object> items = new ArrayList<>();
        iterator.forEachRemaining(items::add);
        return items;
    }

    protected String flattenText(Object value) {
        StringBuilder builder = new StringBuilder();
        appendFlattened(value, builder);
        return builder.toString().trim();
    }

    protected boolean containsIgnoreCase(String text, String token) {
        return text != null && token != null
                && text.toUpperCase(Locale.ROOT).contains(token.toUpperCase(Locale.ROOT));
    }

    protected List<Map<String, String>> systemPrompt(String content) {
        return List.of(Map.of("role", "system", "content", content));
    }

    private void appendFlattened(Object value, StringBuilder builder) {
        if (value == null) {
            return;
        }
        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>) {
            builder.append(value).append(' ');
            return;
        }
        if (value instanceof OutputSchema outputSchema) {
            appendFlattened(outputSchema.getPayload(), builder);
            return;
        }
        if (value instanceof TraceSchema traceSchema) {
            appendFlattened(traceSchema.getPayload(), builder);
            return;
        }
        if (value instanceof ControllerOutput controllerOutput) {
            appendFlattened(controllerOutput.getData(), builder);
            return;
        }
        if (value instanceof ControllerOutputChunk outputChunk) {
            appendFlattened(outputChunk.getControllerPayload(), builder);
            return;
        }
        if (value instanceof ControllerOutputPayload payload) {
            appendFlattened(payload.getData(), builder);
            appendFlattened(payload.getMetadata(), builder);
            return;
        }
        if (value instanceof DataFrame.TextDataFrame textDataFrame) {
            builder.append(textDataFrame.text()).append(' ');
            return;
        }
        if (value instanceof DataFrame.JsonDataFrame jsonDataFrame) {
            appendFlattened(jsonDataFrame.data(), builder);
            return;
        }
        if (value instanceof Span span) {
            appendFlattened(span.getInputs(), builder);
            appendFlattened(span.getOutputs(), builder);
            appendFlattened(span.getOnInvokeData(), builder);
            if (span instanceof TraceWorkflowSpan workflowSpan) {
                appendFlattened(workflowSpan.getStreamOutputs(), builder);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object entryValue : map.values()) {
                appendFlattened(entryValue, builder);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                appendFlattened(item, builder);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                appendFlattened(Array.get(value, index), builder);
            }
            return;
        }
        builder.append(value).append(' ');
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    @AfterEach
    void cleanupSystemTestArtifacts() {
        for (String groupId : groupIds) {
            try {
                Runner.resourceMgr().removeAgentGroup(groupId, null, TagMatchStrategy.ALL, true);
            } catch (Exception ignored) {
                // Best-effort cleanup for system tests.
            }
        }
        for (String agentId : agentIds) {
            try {
                Runner.resourceMgr().removeAgent(agentId, null, TagMatchStrategy.ALL, true);
            } catch (Exception ignored) {
                // Best-effort cleanup for system tests.
            }
        }
        for (String workflowId : workflowIds) {
            try {
                Runner.resourceMgr().removeWorkflow(workflowId, null, TagMatchStrategy.ALL, true);
            } catch (Exception ignored) {
                // Best-effort cleanup for system tests.
            }
        }
        for (String sessionId : sessionIds) {
            try {
                Runner.release(sessionId);
            } catch (Exception ignored) {
                // Best-effort cleanup for system tests.
            }
        }
        Runner.stop();
        Runner.setConfig(RunnerConfig.DEFAULT);
        sessionIds.clear();
        workflowIds.clear();
        agentIds.clear();
        groupIds.clear();
    }

    protected record InvocationCapture(Object result, List<Object> streamItems, String flattenedText) {
    }
}
