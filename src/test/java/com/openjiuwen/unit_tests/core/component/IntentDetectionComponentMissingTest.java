/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.component;

import com.openjiuwen.core.context_engine.ContextStats;
import com.openjiuwen.core.context_engine.ContextWindow;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.BranchRouter;
import com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig;
import com.openjiuwen.core.workflow.component.llm.IntentDetectionExecutable;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/core/component/test_intent_detection_comp.py}.</p>
 */
class IntentDetectionComponentMissingTest {

    @Test
    @SuppressWarnings("unchecked")
    void testInvokeSuccess() {
        AtomicInteger invokeCount = registerModelResponse("{\"class\": \"分类2\", \"reason\": \"ok\"}", null);
        IntentDetectionExecutable executable = executable(config(List.of("name1", "name2", "name3"), "zh", false));

        Map<String, Object> output = (Map<String, Object>) executable.invoke(
                Map.of("query", "你好"),
                new TestSession(),
                null
        );

        assertThat(output).containsEntry("category_name", "name2");
        assertThat(invokeCount).hasValue(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInvokeSuccessAcceptLanguageEn() {
        AtomicInteger invokeCount = registerModelResponse(
                "{\"class\": \"Category2\", \"reason\": \"User asks about travel\"}",
                null
        );
        IntentDetectionExecutable executable = executable(config(List.of("weather", "travel", "other"), "en", false));

        Map<String, Object> output = (Map<String, Object>) executable.invoke(
                Map.of("query", "I want to travel"),
                new TestSession(),
                null
        );

        assertThat(output).containsEntry("category_name", "travel");
        assertThat(output).containsEntry("classification_id", 2);
        assertThat(invokeCount).hasValue(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInvokeSuccessAcceptLanguageEnCaseInsensitive() {
        registerModelResponse("{\"class\": \"category1\", \"reason\": \"weather query\"}", null);
        IntentDetectionExecutable executable = executable(config(List.of("weather"), "en", false));

        Map<String, Object> output = (Map<String, Object>) executable.invoke(
                Map.of("query", "What is the weather"),
                new TestSession(),
                null
        );

        assertThat(output).containsEntry("category_name", "weather");
        assertThat(output).containsEntry("classification_id", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInvokeUsesCompletedHistoryBeforeWritingCurrentTurn() {
        AtomicReference<List<BaseMessage>> llmMessages = new AtomicReference<>(List.of());
        registerModelResponse("{\"class\": \"Category2\", \"reason\": \"travel intent\"}", llmMessages);
        IntentDetectionCompConfig config = config(List.of("weather", "travel"), "en", true);
        IntentDetectionExecutable executable = executable(config);
        RecordingContext context = new RecordingContext(List.of(
                new UserMessage("previous weather query"),
                new AssistantMessage("{\"class\": \"Category1\", \"reason\": \"previous\"}")
        ));

        Map<String, Object> output = (Map<String, Object>) executable.invoke(
                Map.of("query", "current travel query"),
                new TestSession(),
                context
        );

        String userPrompt = llmMessages.get().stream()
                .filter(message -> "user".equals(message.getRole()))
                .findFirst()
                .map(message -> String.valueOf(message.getContent()))
                .orElse("");
        String historyPart = userPrompt.split("Current input:", 2)[0];

        assertThat(output).containsEntry("classification_id", 2);
        assertThat(historyPart).contains("previous weather query");
        assertThat(historyPart).doesNotContain("current travel query");
        assertThat(context.getMessages(null, true))
                .extracting(message -> String.valueOf(message.getContent()))
                .containsExactly(
                        "previous weather query",
                        "{\"class\": \"Category1\", \"reason\": \"previous\"}",
                        "current travel query",
                        "{\"class\": \"Category2\", \"reason\": \"travel intent\"}"
                );
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testStartIntentEndStream() {
        assertThat(true).isTrue();
    }

    private static IntentDetectionExecutable executable(IntentDetectionCompConfig config) {
        return new IntentDetectionExecutable(config).setRouter(new BranchRouter());
    }

    private static IntentDetectionCompConfig config(List<String> categories, String acceptLanguage,
                                                    boolean enableHistory) {
        IntentDetectionCompConfig config = new IntentDetectionCompConfig();
        config.setUserPrompt("en".equals(acceptLanguage) ? "Determine user intent" : "请判断用户意图");
        config.setCategoryNameList(categories);
        config.setAcceptLanguage(acceptLanguage);
        config.setEnableHistory(enableHistory);
        config.setModelConfig(ModelRequestConfig.builder()
                .modelName("gpt-3.5-turbo")
                .temperature(0.7)
                .topP(0.9)
                .build());
        config.setModelClientConfig(ModelClientConfig.builder()
                .clientProvider("OpenAI")
                .apiKey("sk-fake")
                .apiBase("https://api.openai.com/v1")
                .timeout(30)
                .maxRetries(3)
                .verifySsl(false)
                .build());
        return config;
    }

    private static AtomicInteger registerModelResponse(String content, AtomicReference<List<BaseMessage>> messages) {
        AtomicInteger invokeCount = new AtomicInteger();
        Model.registerInvoker("OpenAI", (llmMessages, modelConfig, modelClientConfig, options) -> {
            invokeCount.incrementAndGet();
            if (messages != null) {
                messages.set(List.copyOf(llmMessages));
            }
            return CompletableFuture.completedFuture(new AssistantMessage(content));
        });
        return invokeCount;
    }

    private static final class TestSession extends BaseSession {
    }

    private static final class RecordingContext implements ModelContext {
        private final List<BaseMessage> messages = new ArrayList<>();

        private RecordingContext(List<BaseMessage> messages) {
            this.messages.addAll(messages);
        }

        @Override
        public int length() {
            return messages.size();
        }

        @Override
        public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
            return List.copyOf(messages);
        }

        @Override
        public void setMessages(List<BaseMessage> messages, boolean withHistory) {
            this.messages.clear();
            if (messages != null) {
                this.messages.addAll(messages);
            }
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            int from = Math.max(0, messages.size() - size);
            List<BaseMessage> popped = new ArrayList<>(messages.subList(from, messages.size()));
            messages.subList(from, messages.size()).clear();
            return popped;
        }

        @Override
        public CompletionStage<Void> clearMessages(boolean withHistory) {
            messages.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(BaseMessage message) {
            messages.add(message);
            return CompletableFuture.completedFuture(List.copyOf(messages));
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messages) {
            if (messages != null) {
                this.messages.addAll(messages);
            }
            return CompletableFuture.completedFuture(List.copyOf(this.messages));
        }

        @Override
        public CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages,
                                                               List<ToolInfo> tools,
                                                               Integer windowSize,
                                                               Integer dialogueRound,
                                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(
                    new ContextWindow(List.of(), List.copyOf(messages), List.of(), new ContextStats())
            );
        }

        @Override
        public ContextStats statistic() {
            return new ContextStats();
        }

        @Override
        public String sessionId() {
            return "test-session";
        }

        @Override
        public String contextId() {
            return "test-context";
        }

        @Override
        public TokenCounterPort tokenCounter() {
            return List::size;
        }

        @Override
        public ToolPort reloaderTool() {
            return () -> "reload";
        }
    }
}
