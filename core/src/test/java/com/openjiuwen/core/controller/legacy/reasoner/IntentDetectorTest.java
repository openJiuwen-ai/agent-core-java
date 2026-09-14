/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.reasoner;

import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.legacy.config.IntentDetectionConfig;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests legacy intent detector behavior.
 *
 * <p>Mirrors Python's {@code IntentDetector} in
 * {@code openjiuwen/core/controller/legacy/reasoner/intent_detector.py}.</p>
 */
class IntentDetectorTest {

    @Test
    void processMessageCreatesDirectWorkflowTaskWhenNoWorkflowsExist() {
        CapturingInvoker invoker = new CapturingInvoker(" {\"result\": 1} ");
        IntentDetector detector = detector(config(List.of("Book travel"), false), agentConfig(List.of()), invoker);
        Event event = Event.createUserEvent("book a hotel", "conv-1", "user-1", null);

        List<Task> tasks = detector.processMessage(event).toCompletableFuture().join();

        assertThat(tasks).hasSize(1);
        Task task = tasks.get(0);
        assertThat(task.getAgentId()).isEqualTo("agent-1");
        assertThat(task.getTaskId()).matches("session-1_intent_Book travel_[0-9a-f]{8}");
        assertThat(task.getTaskType()).isEqualTo(TaskType.WORKFLOW);
        assertThat(task.getInput().getTargetId()).isEqualTo("Book travel");
        assertThat(task.getInput().getTargetName()).isEqualTo("Book travel");
        assertThat(task.getInput().getArguments()).isSameAs(event.getContent());
        assertThat(invoker.capturedInputs).hasSize(2);
        assertThat(invoker.capturedInputs.get(0).getContentAsString())
                .contains("分类0：意图不明")
                .contains("分类1：Book travel");
    }

    @Test
    void fencedJsonMatchesWorkflowDescriptionAndCreatesWorkflowTask() {
        CapturingInvoker invoker = new CapturingInvoker("```json\n{\"result\": \"1\"}\n```");
        List<IntentDetector.Workflow> workflows = List.of(
                new IntentDetector.Workflow("workflow-1", "Travel Workflow", "Book travel")
        );
        IntentDetector detector = detector(config(List.of("Book travel"), false), agentConfig(workflows), invoker);
        Event event = Event.createUserEvent("book", "conv-1", "user-1", null);

        List<Task> tasks = detector.processMessage(event).toCompletableFuture().join();

        assertThat(tasks).hasSize(1);
        Task task = tasks.get(0);
        assertThat(task.getInput().getTargetId()).isEqualTo("workflow-1");
        assertThat(task.getInput().getTargetName()).isEqualTo("Travel Workflow");
        assertThat(task.getTaskType()).isEqualTo(TaskType.WORKFLOW);
    }

    @Test
    void tripleQuotedJsonFallsBackToWorkflowNameWhenDescriptionIsBlank() {
        CapturingInvoker invoker = new CapturingInvoker("'''json\n{\"result\": 1}\n'''");
        List<IntentDetector.Workflow> workflows = List.of(
                new IntentDetector.Workflow("workflow-1", "Travel Workflow", "")
        );
        IntentDetector detector = detector(config(List.of("Travel Workflow"), false), agentConfig(workflows), invoker);

        List<Task> tasks = detector.processMessage(Event.createUserEvent("book", "conv-1", "user-1", null))
                .toCompletableFuture()
                .join();

        assertThat(tasks).singleElement()
                .extracting(task -> task.getInput().getTargetId())
                .isEqualTo("workflow-1");
    }

    @Test
    void unknownClassReturnsDefaultIntentAndGeneratesNoTask() {
        CapturingInvoker invoker = new CapturingInvoker("{\"result\": 99}");
        IntentDetector detector = detector(config(List.of("Book travel"), false), agentConfig(List.of()), invoker);

        List<Task> tasks = detector.processMessage(Event.createUserEvent("unknown", "conv-1", "user-1", null))
                .toCompletableFuture()
                .join();

        assertThat(tasks).isEmpty();
    }

    @Test
    void detectionInputIncludesHistoryAndCurrentQueryWhenEnabled() {
        ContextEngine contextEngine = new ContextEngine();
        FakeSession session = new FakeSession("session-1");
        ModelContext context = contextEngine.createContext(ContextEngine.DEFAULT_CONTEXT_ID, session);
        context.addMessages(List.of(
                new UserMessage("old question"),
                new AssistantMessage("old answer")
        )).toCompletableFuture().join();
        CapturingInvoker invoker = new CapturingInvoker("{\"result\": 1}");
        IntentDetector detector = new IntentDetector(
                config(List.of("Book travel"), true),
                agentConfig(List.of()),
                contextEngine,
                session,
                new IntentDetector.ContextEngineChatHistoryProvider(),
                invoker,
                deterministicRandom()
        );

        detector.processMessage(Event.createUserEvent("new question", "conv-1", "user-1", null))
                .toCompletableFuture()
                .join();

        assertThat(invoker.capturedInputs).hasSize(2);
        String userPrompt = invoker.capturedInputs.get(1).getContentAsString();
        assertThat(userPrompt)
                .contains("用户: old question")
                .contains("助手: old answer")
                .contains("当前输入：")
                .contains("new question");
    }

    @Test
    void invalidLlmOutputRaisesParseFailureLikePythonJsonLoad() {
        IntentDetector detector = detector(config(List.of("Book travel"), false), agentConfig(List.of()),
                new CapturingInvoker("not json"));

        assertThatThrownBy(() -> detector.processMessage(Event.createUserEvent("hello", "conv-1", "user-1", null))
                .toCompletableFuture()
                .join())
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failed to parse JSON from LLM output");
    }

    @Test
    void llmFailureIsWrappedAsControllerInvokeFailure() {
        IntentDetector detector = detector(config(List.of("Book travel"), false), agentConfig(List.of()),
                (inputs, modelConfig, session) -> {
                    CompletableFuture<String> failed = new CompletableFuture<>();
                    failed.completeExceptionally(new IllegalStateException("provider unavailable"));
                    return failed;
                });

        assertThatThrownBy(() -> detector.processMessage(Event.createUserEvent("hello", "conv-1", "user-1", null))
                .toCompletableFuture()
                .join())
                .hasMessageContaining("controller_invoke")
                .hasRootCauseMessage("provider unavailable");
    }

    private static IntentDetector detector(IntentDetectionConfig config, IntentDetector.AgentConfig agentConfig,
                                           IntentDetector.LlmInvoker invoker) {
        return new IntentDetector(config, agentConfig, null, new FakeSession("session-1"),
                (contextEngine, session, chatHistoryMaxTurn) -> List.of(), invoker, deterministicRandom());
    }

    private static IntentDetectionConfig config(List<String> categories, boolean enableHistory) {
        return IntentDetectionConfig.builder()
                .categoryList(categories)
                .enableHistory(enableHistory)
                .enableInput(true)
                .chatHistoryMaxTurn(5)
                .build();
    }

    private static IntentDetector.AgentConfig agentConfig(List<IntentDetector.Workflow> workflows) {
        return new IntentDetector.AgentConfig("agent-1", workflows, ModelConfig.builder().build());
    }

    private static SecureRandom deterministicRandom() {
        return new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = (byte) (i + 1);
                }
            }
        };
    }

    private static final class CapturingInvoker implements IntentDetector.LlmInvoker {
        private final String output;
        private List<BaseMessage> capturedInputs = List.of();

        private CapturingInvoker(String output) {
            this.output = output;
        }

        @Override
        public CompletionStage<String> invoke(List<BaseMessage> llmInputs, ModelConfig modelConfig,
                                              AgentSessionApi session) {
            this.capturedInputs = List.copyOf(llmInputs);
            return CompletableFuture.completedFuture(output);
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
