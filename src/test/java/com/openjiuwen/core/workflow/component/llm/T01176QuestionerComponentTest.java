/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
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
 *
 * <p>Mirrors Python's questioner workflow tests in
 * {@code tests/unit_tests/core/component/test_questioner_comp.py}.</p>
 *
 * <p>Mirrors Python's ReAct/questioner extract interrupt regression in
 * {@code tests/unit_tests/agent/react_agent/test_react_agent_questioner_extract_context.py}.</p>
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

    @Test
    void initialAskExtractsLocationAndAppliesDefaultTime() {
        QuestionerConfig config = new QuestionerConfig();
        config.setAcceptLanguage("en");
        config.setFieldNames(List.of(
                field("location", "Location", "string", true, ""),
                field("time", "Time", "string", true, "today")
        ));

        QuestionerDirectReplyHandler handler = handler(config, "{\"location\":\"hangzhou\"}");

        Map<String, Object> output = handler.handle(Map.of("query", "query Hangzhou weather"),
                new TestSession(), null);

        assertEquals("hangzhou", output.get("location"));
        assertEquals("today", output.get("time"));
        assertEquals(ExecutionStatus.END, handler.getState().getStatus());
    }

    @Test
    void questionContentRequestsUserInputBeforeExtraction() {
        QuestionerConfig config = new QuestionerConfig();
        config.setAcceptLanguage("en");
        config.setQuestionContent("Which city's weather?");
        config.setFieldNames(List.of(
                field("location", "Location", "string", true, ""),
                field("time", "Time", "string", true, "today")
        ));

        QuestionerDirectReplyHandler handler = handler(config, "{\"location\":\"hangzhou\"}");

        Map<String, Object> output = handler.handle(Map.of("query", "hello"), new TestSession(), null);

        assertEquals("Which city's weather?", output.get("question"));
        assertEquals(ExecutionStatus.USER_INTERACT, handler.getState().getStatus());
    }

    @Test
    void acceptLanguageZhExtractsChineseJsonFields() {
        QuestionerConfig config = new QuestionerConfig();
        config.setAcceptLanguage("zh");
        config.setFieldNames(List.of(
                field("location", "地点", "string", true, ""),
                field("date", "日期", "string", true, "today")
        ));

        QuestionerDirectReplyHandler handler = handler(config,
                "{\"location\":\"北京\",\"date\":\"2025-02-26\"}");

        Map<String, Object> output = handler.handle(Map.of("query", "查询北京的天气"), new TestSession(), null);

        assertEquals("北京", output.get("location"));
        assertEquals("2025-02-26", output.get("date"));
        assertEquals(ExecutionStatus.END, handler.getState().getStatus());
    }

    @Test
    void acceptLanguageEnExtractsEnglishJsonFields() {
        QuestionerConfig config = new QuestionerConfig();
        config.setAcceptLanguage("en");
        config.setFieldNames(List.of(
                field("location", "Location", "string", true, ""),
                field("date", "Date", "string", true, "today")
        ));

        QuestionerDirectReplyHandler handler = handler(config,
                "{\"location\":\"Shanghai\",\"date\":\"2025-02-26\"}");

        Map<String, Object> output = handler.handle(Map.of("query", "What is the weather in Shanghai"),
                new TestSession(), null);

        assertEquals("Shanghai", output.get("location"));
        assertEquals("2025-02-26", output.get("date"));
        assertEquals(ExecutionStatus.END, handler.getState().getStatus());
    }

    @Test
    void formatContinueAskQuestionFollowsAcceptLanguage() {
        List<FieldInfo> fields = List.of(
                field("location", "Location", "string", true, ""),
                field("date", "Date", "string", true, "")
        );

        String resultEn = QuestionerUtils.formatContinueAskQuestion(fields, "en");
        String resultZh = QuestionerUtils.formatContinueAskQuestion(fields, "zh");

        assertTrue(resultEn.contains("Please provide"));
        assertTrue(resultZh.contains("请您提供"));
    }

    @Test
    void newQuestionerStateDoesNotReusePreviousWorkflowExtraction() {
        QuestionerConfig config = new QuestionerConfig();
        config.setAcceptLanguage("en");
        config.setFieldNames(List.of(field("name", "User name", "string", true, "")));

        Map<String, Object> first = handler(config, "{\"name\":\"张三\"}")
                .handle(Map.of("query", "collect user info"), new TestSession(), null);
        Map<String, Object> second = handler(config, "{\"name\":\"李四\"}")
                .handle(Map.of("query", "collect user info again"), new TestSession(), null);

        assertEquals("张三", first.get("name"));
        assertEquals("李四", second.get("name"));
        assertFalse(String.valueOf(second.get("name")).contains("张三"));
    }

    @Test
    void questionerExtractInterruptThenResumeWritesAssistantMessage() {
        QuestionerConfig config = new QuestionerConfig();
        config.setAcceptLanguage("zh");
        config.setWithChatHistory(true);
        config.setFieldNames(List.of(field("name", "姓名", "string", true, "")));
        QuestionerDirectReplyHandler handler = handler(config, "{}");
        RecordingContext context = new RecordingContext();
        TestSession session = new TestSession("我叫张三");

        Map<String, Object> first = handler.handle(Map.of("query", "帮我处理一下"), session, context);

        assertEquals(ExecutionStatus.USER_INTERACT, handler.getState().getStatus());
        assertTrue(String.valueOf(first.get("question")).contains("姓名"));
        assertEquals(List.of("user", "assistant"), context.messages.stream().map(BaseMessage::getRole).toList());

        handler.model(modelReturning("{\"name\":\"张三\"}"));
        Map<String, Object> second = handler.handle(new LinkedHashMap<>(), session, context);

        assertEquals(ExecutionStatus.END, handler.getState().getStatus());
        assertEquals("张三", second.get("name"));
        assertEquals(List.of("user", "assistant", "user", "assistant"),
                context.messages.stream().map(BaseMessage::getRole).toList());
        assertEquals("我叫张三", context.messages.get(2).getContent());
        assertEquals("{\"name\":\"张三\"}", context.messages.get(3).getContent());
    }

    @Test
    void invalidIntegerTriggersContinueAskForRequiredField() {
        QuestionerConfig config = new QuestionerConfig();
        config.setAcceptLanguage("en");
        config.setFieldNames(List.of(
                field("name", "User name", "string", true, ""),
                field("age", "User age", "integer", true, "")
        ));

        QuestionerDirectReplyHandler handler = handler(config, "{\"name\":\"Bob\",\"age\":3.14}");

        Map<String, Object> output = handler.handle(Map.of("query", "My name is Bob, I am 3.14 years old"),
                new TestSession(), null);

        assertEquals("Please provide information related to: User age", output.get("question"));
        assertFalse(output.containsKey("age"));
        assertEquals(ExecutionStatus.USER_INTERACT, handler.getState().getStatus());
    }

    private static QuestionerDirectReplyHandler handler(QuestionerConfig config, String responseJson) {
        PromptTemplate prompt = PromptTemplate.builder()
                .content(QuestionerDefaultConfig.fromLanguage(config.getAcceptLanguage()).getPromptTemplate())
                .build();
        return new QuestionerDirectReplyHandler()
                .config(config)
                .model(modelReturning(responseJson))
                .state(new QuestionerState().handleEvent(QuestionerEvent.START_EVENT))
                .prompt(prompt);
    }

    private static Model modelReturning(String responseJson) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(responseJson)));
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

    public static final class TestSession extends BaseSession {
        private final String feedback;

        private TestSession() {
            this("");
        }

        private TestSession(String feedback) {
            this.feedback = feedback;
        }

        public String interact(Object question) {
            return feedback;
        }

        public String userLatestInput(Object question) {
            return feedback;
        }
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
