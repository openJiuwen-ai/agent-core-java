/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.utils;

import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.common.utils.HashUtil;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests legacy controller utility behavior.
 *
 * <p>Mirrors Python's {@code MessageHandlerUtils} and {@code ReasonerUtils} in
 * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
 */
class MessageHandlerUtilsTest {

    @AfterEach
    void tearDown() {
        ReasonerUtils.resetResourceManager();
    }

    @Test
    void formatLlmInputsDeepCopiesDictAndMergesKeywords() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("value", "original");
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("name", "Ada");
        inputs.put("nested", nested);
        MessageHandlerUtils.SimpleAgentConfig config = config("hello {{name}} {{extra}}", 2);
        List<BaseMessage> chatHistory = List.of(new UserMessage("old"));

        List<BaseMessage> messages = MessageHandlerUtils.formatLlmInputs(
                inputs,
                chatHistory,
                config,
                Map.of("extra", "kw")
        );

        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().getContentAsString()).isEqualTo("hello Ada kw");
        assertThat(messages.get(1).getContentAsString()).isEqualTo("old");
        assertThat(inputs).doesNotContainKey("extra");
    }

    @Test
    void concatSystemPromptSkipsPromptWhenHistoryAlreadyStartsWithSystem() {
        List<BaseMessage> result = MessageHandlerUtils.concatSystemPromptWithChatHistory(
                List.of(new BaseMessage("system", "new system")),
                List.of(new BaseMessage("system", "existing system"), new UserMessage("hello"))
        );

        assertThat(result)
                .extracting(BaseMessage::getContentAsString)
                .containsExactly("existing system", "hello");
    }

    @Test
    void createTasksFromToolCallsCreatesWorkflowAndPluginWhenNamesBothMatch() {
        MessageHandlerUtils.SimpleAgentConfig config = new MessageHandlerUtils.SimpleAgentConfig(
                "",
                List.of(new MessageHandlerUtils.Workflow("workflow-id", "v2", "dispatch")),
                List.of(new MessageHandlerUtils.Plugin("dispatch")),
                new MessageHandlerUtils.Constrain(1)
        );
        ToolCall toolCall = ToolCall.builder()
                .id("call-1")
                .name("dispatch")
                .arguments("{\"x\":1}")
                .build();

        List<Task> tasks = MessageHandlerUtils.createTasksFromToolCalls(List.of(toolCall), config);

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).getTaskType()).isEqualTo(TaskType.WORKFLOW);
        assertThat(tasks.get(0).getTaskId()).isEqualTo("call-1");
        assertThat(tasks.get(0).getInput().getTargetId()).isEqualTo("workflow-id_v2");
        assertThat(tasks.get(0).getInput().getTargetName()).isEqualTo("dispatch");
        assertThat(tasks.get(0).getInput().getArguments()).isInstanceOf(Map.class);
        assertThat(tasks.get(1).getTaskType()).isEqualTo(TaskType.PLUGIN);
        assertThat(tasks.get(1).getInput().getTargetId()).isEmpty();
    }

    @Test
    void createTasksRaisesWhenNoToolCallMatchesAnyConfiguredTool() {
        ToolCall toolCall = ToolCall.builder()
                .id("call-1")
                .name("missing")
                .arguments("{}")
                .build();

        assertThatThrownBy(() -> MessageHandlerUtils.createTasksFromToolCalls(List.of(toolCall), config("", 1)))
                .hasMessageContaining("agent tool not found");
    }

    @Test
    void determineTaskTypeAndInteractionResultMirrorPythonBranches() {
        MessageHandlerUtils.SimpleAgentConfig config = new MessageHandlerUtils.SimpleAgentConfig(
                "",
                List.of(new MessageHandlerUtils.Workflow("workflow-id", "v1", "flow")),
                List.of(new MessageHandlerUtils.Plugin("plug")),
                new MessageHandlerUtils.Constrain(1)
        );

        assertThat(MessageHandlerUtils.determineTaskType("flow", config)).isEqualTo(TaskType.WORKFLOW);
        assertThat(MessageHandlerUtils.determineTaskType("plug", config)).isEqualTo(TaskType.PLUGIN);
        assertThat(MessageHandlerUtils.isInteractionResult(Map.of("error", "yes", "value", List.of("one"))))
                .isTrue();
        assertThat(MessageHandlerUtils.isInteractionResult(Map.of("error", false, "value", List.of("one"))))
                .isFalse();
        assertThat(MessageHandlerUtils.isInteractionResult(Map.of("error", true, "value", "one")))
                .isFalse();
    }

    @Test
    void userMessageHelpersSkipDuplicatesAndPostToolMessages() {
        ContextEngine contextEngine = new ContextEngine();
        FakeSession session = new FakeSession("session-1");
        ModelContext context = contextEngine.createContext(ContextEngine.DEFAULT_CONTEXT_ID, session);

        MessageHandlerUtils.addUserMessage("hello", contextEngine, session).toCompletableFuture().join();
        MessageHandlerUtils.addUserMessage("hello", contextEngine, session).toCompletableFuture().join();

        assertThat(context.getMessages(null, true)).hasSize(1);
        assertThat(MessageHandlerUtils.shouldAddUserMessage("hello", contextEngine, session)).isFalse();
        context.addMessages(ToolMessage.builder().content("tool output").toolCallId("call-1").build())
                .toCompletableFuture()
                .join();
        assertThat(MessageHandlerUtils.shouldAddUserMessage("next", contextEngine, session)).isFalse();
    }

    @Test
    void addToolResultSerializesTaskOutputAndToolCallId() {
        ContextEngine contextEngine = new ContextEngine();
        FakeSession session = new FakeSession("session-1");
        ModelContext context = contextEngine.createContext(ContextEngine.DEFAULT_CONTEXT_ID, session);
        OutputSchema outputSchema = new OutputSchema("chunk", 0, Map.of("output", List.of("ok")));
        Event event = Event.createTaskCompleted("conversation-1", "task-1", new TaskResultBox(outputSchema),
                "workflow-1", List.of());

        MessageHandlerUtils.addToolResult(event, contextEngine, session).toCompletableFuture().join();

        BaseMessage message = context.getMessages(null, true).getFirst();
        assertThat(message).isInstanceOf(ToolMessage.class);
        assertThat(message.getContentAsString()).isEqualTo("[\"ok\"]");
        assertThat(((ToolMessage) message).getToolCallId()).isEqualTo("task-1");
    }

    @Test
    void filterInputsKeepsSchemaOrderAndRaisesForMissingRequiredField() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("first", Map.of("required", true));
        schema.put("second", Map.of("required", false));
        schema.put("third", "loose");

        assertThat(MessageHandlerUtils.filterInputs(schema, Map.of("first", 1, "third", 3)))
                .containsExactly(Map.entry("first", 1), Map.entry("third", 3));
        assertThatThrownBy(() -> MessageHandlerUtils.filterInputs(schema, Map.of("second", 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required parameter: first");
    }

    @Test
    void getChatHistoryUsesPythonTailSliceIncludingZeroTurns() {
        ContextEngine contextEngine = new ContextEngine();
        FakeSession session = new FakeSession("session-1");
        ModelContext context = contextEngine.createContext(ContextEngine.DEFAULT_CONTEXT_ID, session);
        context.addMessages(List.of(new UserMessage("one"), new AssistantMessage("two"), new UserMessage("three")))
                .toCompletableFuture()
                .join();

        assertThat(ReasonerUtils.getChatHistory(contextEngine, session, 1))
                .extracting(BaseMessage::getContentAsString)
                .containsExactly("two", "three");
        assertThat(ReasonerUtils.getChatHistory(contextEngine, session, 0))
                .extracting(BaseMessage::getContentAsString)
                .containsExactly("one", "two", "three");
    }

    @Test
    void getModelRegistersMissingModelWithGeneratedModelId() {
        Model.registerInvoker("unit-test-provider-t00943", (messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
        CapturingResourceManager resourceManager = new CapturingResourceManager();
        ReasonerUtils.setResourceManager(resourceManager);
        BaseModelInfo modelInfo = BaseModelInfo.builder()
                .apiKey("key")
                .apiBase("https://example.invalid")
                .modelName("unit-model")
                .temperature(0.2d)
                .topP(0.8d)
                .customHeaders(Map.of("X-Test", "yes"))
                .build();
        ModelConfig modelConfig = ModelConfig.builder()
                .modelProvider("unit-test-provider-t00943")
                .modelInfo(modelInfo)
                .build();

        Model model = ReasonerUtils.getModel(modelConfig, new FakeSession("session-1"))
                .toCompletableFuture()
                .join();

        String expectedId = HashUtil.generateKey("key", "https://example.invalid", "unit-test-provider-t00943");
        assertThat(model).isNotNull();
        assertThat(resourceManager.addedModelId).isEqualTo(expectedId);
        assertThat(resourceManager.requestedModelIds).containsExactly(expectedId, expectedId);
    }

    private static MessageHandlerUtils.SimpleAgentConfig config(Object promptTemplate, int reservedMaxChatRounds) {
        return new MessageHandlerUtils.SimpleAgentConfig(
                promptTemplate,
                List.of(),
                List.of(),
                new MessageHandlerUtils.Constrain(reservedMaxChatRounds)
        );
    }

    private static final class TaskResultBox {
        private final Object output;

        private TaskResultBox(Object output) {
            this.output = output;
        }

        public Object getOutput() {
            return output;
        }
    }

    private static final class CapturingResourceManager implements ReasonerUtils.ResourceManagerView {
        private final List<String> requestedModelIds = new ArrayList<>();
        private String addedModelId;
        private Supplier<Model> supplier;
        private Model model;

        @Override
        public CompletionStage<Model> getModel(String modelId, AgentSessionApi session) {
            requestedModelIds.add(modelId);
            if (model == null && supplier != null) {
                model = supplier.get();
            }
            return CompletableFuture.completedFuture(model);
        }

        @Override
        public void addModel(String modelId, Supplier<Model> modelSupplier) {
            this.addedModelId = modelId;
            this.supplier = modelSupplier;
        }
    }

    private static final class FakeSession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;

        private FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> data) {
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return new ArrayList<>().iterator();
        }
    }
}
