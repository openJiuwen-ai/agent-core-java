/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.ExternalTool;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.external.ExternalToolResult;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReActAgentExternalToolPendingTest {
    private static final String EXTERNAL_TOOL_NAME = "frontend_read_text_input";
    private static final String NORMAL_TOOL_NAME = "countingTool";
    private static final String EXACT_RESULT_ERROR =
            "External tool results must contain exactly all pending tool_call_id values";
    private static final String PENDING_REQUIRES_RESULTS_ERROR =
            "External tool results are required before continuing this conversation";

    private final List<String> registeredToolIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String toolId : registeredToolIds) {
            Runner.resourceMgr().removeTool(toolId);
        }
    }

    @Test
    void invokeReturnsPendingAndDoesNotExecuteSameRoundNormalToolWhenExternalToolIsPresent() {
        AtomicInteger normalToolInvokes = new AtomicInteger();
        ScriptedReActAgent agent = pendingAgent(normalToolInvokes);
        MemorySession session = new MemorySession("pending-session");

        Object result = agent.invoke(Map.of("query", "read frontend"), session).toCompletableFuture().join();

        Map<String, Object> resultMap = objectMap(result);
        assertThat(resultMap).containsEntry("result_type", "external_tool_call_required");
        List<Map<String, Object>> calls = externalToolCalls(resultMap);
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0))
                .containsEntry("tool_call_id", "call-external")
                .containsEntry("tool_name", EXTERNAL_TOOL_NAME)
                .containsEntry("arguments", "{\"field_id\":\"demo_external_text\"}");
        assertThat(normalToolInvokes).hasValue(0);
        assertThat(session.getState(ReActAgent.EXTERNAL_TOOL_PENDING_KEY)).isNotNull();
    }

    @Test
    void pendingRequiresExternalToolResultsBeforeAcceptingNewUserQuery() {
        AtomicInteger normalToolInvokes = new AtomicInteger();
        ScriptedReActAgent agent = pendingAgent(normalToolInvokes);
        MemorySession session = new MemorySession("pending-new-query-session");
        agent.invoke(Map.of("query", "read frontend"), session).toCompletableFuture().join();

        Object result = agent.invoke(Map.of("query", "new user turn"), session).toCompletableFuture().join();

        assertThat(objectMap(result))
                .containsEntry("result_type", "error")
                .containsEntry("output", PENDING_REQUIRES_RESULTS_ERROR);
        assertThat(session.getState(ReActAgent.EXTERNAL_TOOL_PENDING_KEY)).isNotNull();
        assertThat(agent.modelCallCount()).isEqualTo(1);
        assertThat(normalToolInvokes).hasValue(0);
        assertThat(currentMessages(agent, session).stream()
                .filter(UserMessage.class::isInstance)
                .map(BaseMessage::getContentAsString))
                .doesNotContain("new user turn");
    }

    @Test
    void resumeRequiresExactlyAllExternalToolCallIdsAndKeepsPendingOnFailure() {
        AtomicInteger normalToolInvokes = new AtomicInteger();
        ScriptedReActAgent agent = pendingAgent(normalToolInvokes);
        MemorySession session = new MemorySession("invalid-resume-session");
        agent.invoke(Map.of("query", "read frontend"), session).toCompletableFuture().join();
        assertThat(agent.modelCallCount()).isEqualTo(1);

        assertInvalidResumeKeepsPending(agent, session, normalToolInvokes,
                Map.of("external_tool_results", List.of()));
        assertInvalidResumeKeepsPending(agent, session, normalToolInvokes,
                Map.of("external_tool_results", List.of(
                        externalResult("call-external", "from browser"),
                        externalResult("call-unknown", "unknown")
                )));
        assertInvalidResumeKeepsPending(agent, session, normalToolInvokes,
                Map.of("external_tool_results", List.of(
                        externalResult("call-external", "first"),
                        externalResult("call-external", "second")
                )));
    }

    @Test
    void externalToolResultInputRequiresResultOrError() {
        assertThatThrownBy(() -> ExternalToolResult.fromInput(List.of(Map.of(
                "tool_call_id", "call-1"
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("result or error");
    }

    @Test
    void resumeSavesToolMessagesBeforeReturningWorkflowInterrupt() {
        AtomicInteger normalToolInvokes = new AtomicInteger();
        WorkflowInterruptingAgent agent = workflowInterruptingPendingAgent(normalToolInvokes);
        MemorySession session = new MemorySession("resume-workflow-interrupt-session");
        agent.invoke(Map.of("query", "read frontend"), session).toCompletableFuture().join();
        int contextUpdatesBeforeResume = session.contextUpdateCount();

        Object result = agent.invoke(Map.of(
                "external_tool_results", List.of(externalResult("call-external", "from browser"))
        ), session).toCompletableFuture().join();

        assertThat(objectMap(result)).containsEntry("result_type", "interrupt");
        assertThat(normalToolInvokes).hasValue(1);
        assertThat(session.contextUpdateCount()).isGreaterThan(contextUpdatesBeforeResume);
        assertThat(savedToolMessages(session)).extracting(ToolMessage::getToolCallId)
                .contains("call-external", "call-normal");
    }

    @Test
    void resumeAppendsMultimodalUserMessageOnlyAfterAllToolMessages() {
        AtomicInteger normalToolInvokes = new AtomicInteger();
        ScriptedReActAgent agent = normalFirstMultimodalPendingAgent(normalToolInvokes);
        MemorySession session = new MemorySession("resume-multimodal-order-session");
        agent.invoke(Map.of("query", "read image and frontend"), session).toCompletableFuture().join();

        agent.invoke(Map.of(
                "external_tool_results", List.of(externalResult("call-external", "from browser"))
        ), session).toCompletableFuture().join();

        List<BaseMessage> messages = agent.modelMessages().get(1);
        int normalToolIndex = indexOfToolMessage(messages, "call-normal");
        int externalToolIndex = indexOfToolMessage(messages, "call-external");
        int multimodalUserIndex = indexOfMultimodalUserMessage(messages);
        assertThat(normalToolIndex).isLessThan(multimodalUserIndex);
        assertThat(externalToolIndex).isLessThan(multimodalUserIndex);
        assertThat(normalToolInvokes).hasValue(1);
    }

    @Test
    void streamingResumeWritesToolResultOutputsForExternalAndNormalCallsInPendingOrder() {
        AtomicInteger normalToolInvokes = new AtomicInteger();
        ScriptedReActAgent agent = pendingAgent(normalToolInvokes);
        MemorySession session = new MemorySession("streaming-resume-tool-results-session");
        agent.invoke(Map.of("query", "read frontend"), session, Map.of("_streaming", true))
                .toCompletableFuture()
                .join();

        agent.invoke(Map.of(
                "external_tool_results", List.of(externalResult("call-external", "from browser"))
        ), session, Map.of("_streaming", true)).toCompletableFuture().join();

        assertThat(outputToolCallIds(outputsOfType(session, "tool_result")))
                .containsExactly("call-external", "call-normal");
    }

    @Test
    void resumeConsumesPendingForceFinishAfterToolResultsBeforeCallingModelAgain() {
        AtomicInteger normalToolInvokes = new AtomicInteger();
        ScriptedReActAgent agent = pendingAgent(normalToolInvokes);
        MemorySession session = new MemorySession("resume-force-finish-session");
        agent.invoke(Map.of("query", "read frontend"), session).toCompletableFuture().join();
        Map<String, Object> forced = new LinkedHashMap<>(Map.of(
                "output", "forced after resume tools",
                "result_type", "answer"
        ));
        agent.getAgentCallbackManager().registerCallback(AgentCallbackEvent.BEFORE_INVOKE, context -> {
            if (isExternalResumeInvoke(context)) {
                context.requestForceFinish(forced);
            }
            return CompletableFuture.completedFuture(null);
        }, 10).toCompletableFuture().join();

        Object result = agent.invoke(Map.of(
                "external_tool_results", List.of(externalResult("call-external", "from browser"))
        ), session).toCompletableFuture().join();

        assertThat(objectMap(result)).isEqualTo(forced);
        assertThat(normalToolInvokes).hasValue(1);
        assertThat(session.getState(ReActAgent.EXTERNAL_TOOL_PENDING_KEY)).isNull();
        assertThat(agent.modelCallCount()).isEqualTo(1);
    }

    @Test
    void resumeWritesExternalToolMessageExecutesNormalToolInOriginalOrderAndContinuesModel() {
        AtomicInteger normalToolInvokes = new AtomicInteger();
        ScriptedReActAgent agent = pendingAgent(normalToolInvokes);
        MemorySession session = new MemorySession("valid-resume-session");
        agent.invoke(Map.of("query", "read frontend"), session).toCompletableFuture().join();

        Object result = agent.invoke(Map.of(
                "external_tool_results", List.of(externalResult("call-external", "from browser"))
        ), session).toCompletableFuture().join();

        assertThat(normalToolInvokes).hasValue(1);
        assertThat(objectMap(result))
                .containsEntry("result_type", "answer")
                .containsEntry("output", "final answer");
        assertThat(session.getState(ReActAgent.EXTERNAL_TOOL_PENDING_KEY)).isNull();

        List<ToolMessage> toolMessages = agent.modelMessages().get(1).stream()
                .filter(ToolMessage.class::isInstance)
                .map(ToolMessage.class::cast)
                .toList();
        assertThat(toolMessages).extracting(ToolMessage::getToolCallId)
                .containsExactly("call-external", "call-normal");
        assertThat(toolMessages).extracting(BaseMessage::getName)
                .containsExactly(EXTERNAL_TOOL_NAME, NORMAL_TOOL_NAME);
    }

    @Test
    void resumeWithQueryDoesNotAppendUserMessage() {
        AtomicInteger normalToolInvokes = new AtomicInteger();
        ScriptedReActAgent agent = pendingAgent(normalToolInvokes);
        MemorySession session = new MemorySession("resume-query-session");
        agent.invoke(Map.of("query", "read frontend"), session).toCompletableFuture().join();

        agent.invoke(Map.of(
                "query", "this resume query must not be appended",
                "external_tool_results", List.of(externalResult("call-external", "from browser"))
        ), session).toCompletableFuture().join();

        assertThat(agent.modelMessages().get(1).stream()
                .filter(UserMessage.class::isInstance)
                .map(BaseMessage::getContentAsString))
                .doesNotContain("this resume query must not be appended");
    }

    private void assertInvalidResumeKeepsPending(ScriptedReActAgent agent,
                                                 MemorySession session,
                                                 AtomicInteger normalToolInvokes,
                                                 Map<String, Object> inputs) {
        Object result = agent.invoke(inputs, session).toCompletableFuture().join();

        Map<String, Object> resultMap = objectMap(result);
        assertThat(resultMap).containsEntry("result_type", "error");
        assertThat(resultMap.get("output")).isEqualTo(EXACT_RESULT_ERROR);
        assertThat(session.getState(ReActAgent.EXTERNAL_TOOL_PENDING_KEY)).isNotNull();
        assertThat(agent.modelCallCount()).isEqualTo(1);
        assertThat(normalToolInvokes).hasValue(0);
    }

    private ScriptedReActAgent pendingAgent(AtomicInteger normalToolInvokes) {
        ScriptedReActAgent agent = new ScriptedReActAgent(List.of(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(externalToolCall(), normalToolCall()))
                        .build(),
                new AssistantMessage("final answer")
        ));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        config.setMaxIterations(3);
        agent.configure(config);
        agent.getAbilityManager().add(externalTool());
        registerNormalTool(agent, normalToolInvokes);
        return agent;
    }

    private ScriptedReActAgent normalFirstMultimodalPendingAgent(AtomicInteger normalToolInvokes) {
        ScriptedReActAgent agent = new ScriptedReActAgent(List.of(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(normalToolCall(), externalToolCall()))
                        .build(),
                new AssistantMessage("final answer")
        ));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        config.setMaxIterations(3);
        agent.configure(config);
        agent.getAbilityManager().add(externalTool());
        registerImageTool(agent, normalToolInvokes);
        return agent;
    }

    private WorkflowInterruptingAgent workflowInterruptingPendingAgent(AtomicInteger normalToolInvokes) {
        WorkflowInterruptingAgent agent = new WorkflowInterruptingAgent(List.of(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(externalToolCall(), normalToolCall()))
                        .build(),
                new AssistantMessage("final answer")
        ));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        config.setMaxIterations(3);
        agent.configure(config);
        agent.getAbilityManager().add(externalTool());
        registerNormalTool(agent, normalToolInvokes);
        return agent;
    }

    private void registerNormalTool(ReActAgent agent, AtomicInteger normalToolInvokes) {
        CountingTool tool = new CountingTool(unique("counting-tool"), normalToolInvokes);
        Runner.resourceMgr().addTool(tool);
        registeredToolIds.add(tool.getCard().getId());
        agent.getAbilityManager().add(tool.getCard());
    }

    private void registerImageTool(ReActAgent agent, AtomicInteger normalToolInvokes) {
        ImageTool tool = new ImageTool(unique("image-tool"), normalToolInvokes);
        Runner.resourceMgr().addTool(tool);
        registeredToolIds.add(tool.getCard().getId());
        agent.getAbilityManager().add(tool.getCard());
    }

    private static ToolCall externalToolCall() {
        return ToolCall.builder()
                .id("call-external")
                .name(EXTERNAL_TOOL_NAME)
                .arguments("{\"field_id\":\"demo_external_text\"}")
                .build();
    }

    private static ToolCall normalToolCall() {
        return ToolCall.builder()
                .id("call-normal")
                .name(NORMAL_TOOL_NAME)
                .arguments("{\"value\":\"later\"}")
                .build();
    }

    private static ExternalTool externalTool() {
        return new ExternalTool(ToolCard.builder()
                .id("external." + EXTERNAL_TOOL_NAME)
                .name(EXTERNAL_TOOL_NAME)
                .description("Read frontend text input")
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build());
    }

    private static Map<String, Object> externalResult(String toolCallId, String text) {
        return Map.of(
                "tool_call_id", toolCallId,
                "result", Map.of("field_id", "demo_external_text", "text", text)
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> externalToolCalls(Map<String, Object> result) {
        assertThat(result.get("external_tool_calls")).isInstanceOf(List.class);
        return (List<Map<String, Object>>) result.get("external_tool_calls");
    }

    private static List<BaseMessage> currentMessages(ScriptedReActAgent agent, MemorySession session) {
        return agent.getContextEngine().createContext(null, session).getMessages(null, true);
    }

    @SuppressWarnings("unchecked")
    private static List<ToolMessage> savedToolMessages(MemorySession session) {
        Object rawContext = session.getState("context");
        assertThat(rawContext).isInstanceOf(Map.class);
        Object contextState = ((Map<String, Object>) rawContext).get("default_context_id");
        assertThat(contextState).isInstanceOf(Map.class);
        Object rawMessages = ((Map<String, Object>) contextState).get("messages");
        assertThat(rawMessages).isInstanceOf(List.class);
        return ((List<BaseMessage>) rawMessages).stream()
                .filter(ToolMessage.class::isInstance)
                .map(ToolMessage.class::cast)
                .toList();
    }

    private static int indexOfToolMessage(List<BaseMessage> messages, String toolCallId) {
        for (int i = 0; i < messages.size(); i++) {
            BaseMessage message = messages.get(i);
            if (message instanceof ToolMessage toolMessage && toolCallId.equals(toolMessage.getToolCallId())) {
                return i;
            }
        }
        throw new AssertionError("ToolMessage not found: " + toolCallId);
    }

    private static int indexOfMultimodalUserMessage(List<BaseMessage> messages) {
        for (int i = 0; i < messages.size(); i++) {
            BaseMessage message = messages.get(i);
            if (message instanceof UserMessage && message.getContent() instanceof List<?> content
                    && content.stream().anyMatch(ReActAgentExternalToolPendingTest::isImageUrlContent)) {
                return i;
            }
        }
        throw new AssertionError("Multimodal UserMessage not found");
    }

    private static boolean isImageUrlContent(Object item) {
        return item instanceof Map<?, ?> map && "image_url".equals(map.get("type"));
    }

    private static List<OutputSchema> outputsOfType(MemorySession session, String type) {
        List<OutputSchema> outputs = new ArrayList<>();
        Iterator<Object> iterator = session.streamIterator();
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (item instanceof OutputSchema outputSchema && type.equals(outputSchema.getType())) {
                outputs.add(outputSchema);
            }
        }
        return outputs;
    }

    private static List<String> outputToolCallIds(List<OutputSchema> outputs) {
        return outputs.stream().map(output -> String.valueOf(outputPayload(output).get("tool_call_id"))).toList();
    }

    private static boolean isExternalResumeInvoke(AgentCallbackContext context) {
        if (!(context.getInputs() instanceof InvokeInputs invokeInputs)) {
            return false;
        }
        return "".equals(invokeInputs.getQuery())
                && context.getSession() != null
                && context.getSession().getState(ReActAgent.EXTERNAL_TOOL_PENDING_KEY) != null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> outputPayload(OutputSchema output) {
        assertThat(output.getPayload()).isInstanceOf(Map.class);
        return (Map<String, Object>) output.getPayload();
    }

    private static AgentCard agentCard(String name) {
        return new AgentCard(name + "-" + System.nanoTime(), name, "test agent");
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static class ScriptedReActAgent extends ReActAgent {
        private final List<AssistantMessage> responses;
        private final List<List<BaseMessage>> modelMessages = new ArrayList<>();
        private int index;

        private ScriptedReActAgent(List<AssistantMessage> responses) {
            super(agentCard("external-pending-agent"));
            this.responses = responses;
        }

        @Override
        public Object callModel(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
            modelMessages.add(new ArrayList<>(context.getMessages(null, true)));
            AssistantMessage response = responses.get(Math.min(index, responses.size() - 1));
            index++;
            return response;
        }

        private int modelCallCount() {
            return modelMessages.size();
        }

        private List<List<BaseMessage>> modelMessages() {
            return modelMessages;
        }
    }

    private static final class WorkflowInterruptingAgent extends ScriptedReActAgent {
        private WorkflowInterruptingAgent(List<AssistantMessage> responses) {
            super(responses);
        }

        @Override
        public InterruptionState afterExecuteToolCall(List<AbilityManager.ExecutionResult> results,
                                                      List<ToolCall> toolCalls,
                                                      AssistantMessage aiMessage,
                                                      int iteration,
                                                      String originalQuery) {
            InterruptionState state = new InterruptionState();
            state.setAiMessage(aiMessage);
            state.setIteration(iteration);
            state.setOriginalQuery(originalQuery);
            WorkflowInterruptEntry entry = new WorkflowInterruptEntry(
                    normalToolCall(),
                    List.of("component-1"),
                    Map.of("status", "interrupted"),
                    null
            );
            state.setInterruptedWorkflows(Map.of("workflow-1", entry));
            state.setPendingWorkflowId("workflow-1");
            state.setPendingComponentId("component-1");
            return state;
        }
    }

    private static final class CountingTool extends Tool {
        private final AtomicInteger invokes;

        private CountingTool(String id, AtomicInteger invokes) {
            super(ToolCard.builder()
                    .id(id)
                    .name(NORMAL_TOOL_NAME)
                    .description("Counting tool")
                    .inputParams(Map.of("type", "object"))
                    .build());
            this.invokes = invokes;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            invokes.incrementAndGet();
            return Map.of("value", inputs.get("value"), "status", "counted");
        }
    }

    private static final class ImageTool extends Tool {
        private final AtomicInteger invokes;

        private ImageTool(String id, AtomicInteger invokes) {
            super(ToolCard.builder()
                    .id(id)
                    .name(NORMAL_TOOL_NAME)
                    .description("Image tool")
                    .inputParams(Map.of("type", "object"))
                    .build());
            this.invokes = invokes;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            invokes.incrementAndGet();
            return Map.of("data", Map.of("multimodal", List.of(Map.of(
                    "type", "image",
                    "source_path", "demo.png",
                    "data_url", "data:image/png;base64,AAAA"
            ))));
        }
    }

    public static final class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();
        private int contextUpdateCount;

        private MemorySession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            if (data.containsKey("context")) {
                contextUpdateCount++;
            }
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }

        private int contextUpdateCount() {
            return contextUpdateCount;
        }
    }
}
