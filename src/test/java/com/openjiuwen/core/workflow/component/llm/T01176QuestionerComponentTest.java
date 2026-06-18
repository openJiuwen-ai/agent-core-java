/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.context_engine.ContextStats;
import com.openjiuwen.core.context_engine.ContextWindow;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.BaseSession;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code questioner_comp} in
 * {@code openjiuwen/core/workflow/components/llm/questioner_comp.py}.
 */
class T01176QuestionerComponentTest {

    @Test
    void formatTemplateUsesPythonNoneForMissingFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", "Ada");

        String formatted = QuestionerUtils.formatTemplate("hello {{name}} {{missing}}", fields);

        assertEquals("hello Ada None", formatted);
    }

    @Test
    void validateInputsRejectsNonMappingInputs() {
        assertThrows(RuntimeException.class, () -> QuestionerUtils.validateInputs("raw"));
    }

    @Test
    void extractionConvertsTypesPreservesUnknownFieldsAndWritesContext() {
        QuestionerConfig config = new QuestionerConfig();
        config.setAcceptLanguage("en");
        config.setWithChatHistory(true);
        config.setFieldNames(List.of(
                field("age", "Age", "integer", true, ""),
                field("active", "Active", "boolean", false, "")
        ));

        QuestionerDirectReplyHandler handler = handler(
                config,
                "{\"age\":\"18\",\"active\":\"false\",\"unknown\":7,\"missing\":null}"
        );
        RecordingContext context = new RecordingContext();

        Map<String, Object> output = handler.handle(Map.of("query", "my age is 18"), new TestSession(), context);

        assertEquals(18, output.get("age"));
        assertEquals("7", output.get("unknown"));
        assertFalse(output.containsKey("active"), "Python if-v truthiness does not persist false values");
        assertEquals(ExecutionStatus.END, handler.getState().getStatus());
        assertEquals(2, context.messages.size());
        assertEquals("user", context.messages.get(0).getRole());
        assertEquals("assistant", context.messages.get(1).getRole());
        assertTrue(context.messages.get(1).getContent().toString().contains("\"age\":18"));
    }

    @Test
    void requiredFalseBooleanContinuesAsNotExtractedLikePython() {
        QuestionerConfig config = new QuestionerConfig();
        config.setAcceptLanguage("en");
        config.setWithChatHistory(true);
        config.setFieldNames(List.of(field("active", "Active", "boolean", true, "")));

        QuestionerDirectReplyHandler handler = handler(config, "{\"active\":false}");
        RecordingContext context = new RecordingContext();

        Map<String, Object> output = handler.handle(Map.of("query", "no"), new TestSession(), context);

        assertEquals("Please provide information related to: Active", output.get("question"));
        assertFalse(output.containsKey("active"));
        assertEquals(ExecutionStatus.USER_INTERACT, handler.getState().getStatus());
        assertEquals("Please provide information related to: Active", context.messages.get(1).getContent());
    }

    private static QuestionerDirectReplyHandler handler(QuestionerConfig config, String responseJson) {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(responseJson)));
        PromptTemplate prompt = PromptTemplate.builder()
                .content(QuestionerDefaultConfig.fromLanguage(config.getAcceptLanguage()).getPromptTemplate())
                .build();
        return new QuestionerDirectReplyHandler()
                .config(config)
                .model(model)
                .state(new QuestionerState().handleEvent(QuestionerEvent.START_EVENT))
                .prompt(prompt);
    }

    private static FieldInfo field(String name, String description, String type, boolean required, Object defaultValue) {
        FieldInfo field = new FieldInfo();
        field.setFieldName(name);
        field.setDescription(description);
        field.setType(type);
        field.setRequired(required);
        field.setDefaultValue(defaultValue);
        return field;
    }

    private static final class TestSession extends BaseSession {
    }

    private static final class RecordingContext implements ModelContext {
        private final List<BaseMessage> messages = new ArrayList<>();

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
