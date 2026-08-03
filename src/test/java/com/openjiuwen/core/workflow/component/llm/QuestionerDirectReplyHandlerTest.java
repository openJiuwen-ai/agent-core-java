/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.session.NodeSessionApi;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class QuestionerDirectReplyHandlerTest {
    @Test
    void handle_withChatHistory_writesQuestionerMessagesAndFlatFinalFields() throws Exception {
        QuestionerConfig config = weatherConfig(true);
        Model model = modelReturning(
                "{\"location\":\"杭州\",\"date\":\"今日\",\"weather\":null,\"temperature\":null}",
                "{\"location\":null,\"date\":null,\"weather\":\"多云\",\"temperature\":null}",
                "{\"location\":null,\"date\":null,\"weather\":null,\"temperature\":20}");
        List<BaseMessage> messages = new ArrayList<>();
        ModelContext context = contextBackedBy(messages);
        NodeSessionApi session = mock(NodeSessionApi.class);
        when(session.interact(any())).thenReturn("天气多云", "温度20度");
        when(session.userLatestInput(any())).thenReturn("温度20度");

        QuestionerDirectReplyHandler handler = new QuestionerDirectReplyHandler()
                .config(config)
                .model(model)
                .state(new QuestionerState())
                .prompt(defaultPrompt());

        handler.handle(Map.of("query", "杭州今日"), session, context);
        handler.handle(Map.of(), session, context);
        handler.handle(Map.of(), session, context);

        assertEquals(List.of("user", "assistant", "user", "assistant", "user", "assistant"),
                messages.stream().map(BaseMessage::getRole).toList());
        assertEquals(List.of(
                        "杭州今日",
                        "请您提供天气, 温度相关的信息",
                        "天气多云",
                        "请您提供温度相关的信息",
                        "温度20度",
                        "{\"temperature\": \"20\", \"location\": \"杭州\", \"date\": \"今日\", \"weather\": \"多云\"}"),
                messages.stream().map(BaseMessage::getContentAsString).toList());
    }

    @Test
    void handle_withoutChatHistory_doesNotWriteWorkflowContext() throws Exception {
        QuestionerConfig config = weatherConfig(false);
        Model model = modelReturning(
                "{\"location\":\"杭州\",\"date\":\"今日\",\"weather\":\"晴\",\"temperature\":\"25\"}");
        List<BaseMessage> messages = new ArrayList<>();
        ModelContext context = contextBackedBy(messages);

        new QuestionerDirectReplyHandler()
                .config(config)
                .model(model)
                .state(new QuestionerState())
                .prompt(defaultPrompt())
                .handle(Map.of("query", "杭州今日天气晴温度25度"), mock(NodeSessionApi.class), context);

        assertEquals(List.of(), messages);
    }

    @Test
    void handle_restoredFieldOrder_writesCurrentFieldsThenConfiguredFields() throws Exception {
        QuestionerConfig config = weatherConfig(true);
        Model model = modelReturning(
                "{\"temperature\":20,\"date\":null,\"weather\":null,\"location\":null}");
        List<BaseMessage> messages = new ArrayList<>();
        ModelContext context = contextBackedBy(messages);
        NodeSessionApi session = mock(NodeSessionApi.class);
        when(session.interact(any())).thenReturn("温度20度");

        Map<String, Object> restoredFields = new LinkedHashMap<>();
        restoredFields.put("date", "今日");
        restoredFields.put("weather", "多云");
        restoredFields.put("location", "杭州");
        QuestionerState restoredState = new QuestionerState(
                1, "", "请您提供温度相关的信息", restoredFields, ExecutionStatus.USER_INTERACT);

        new QuestionerDirectReplyHandler()
                .config(config)
                .model(model)
                .state(restoredState)
                .prompt(defaultPrompt())
                .handle(Map.of(), session, context);

        assertEquals("{\"temperature\": \"20\", \"location\": \"杭州\", \"date\": \"今日\", "
                        + "\"weather\": \"多云\"}",
                messages.get(messages.size() - 1).getContentAsString());
    }

    private static QuestionerConfig weatherConfig(boolean withChatHistory) {
        QuestionerConfig config = new QuestionerConfig();
        config.setExtractFieldsFromResponse(true);
        config.setFieldNames(List.of(
                FieldInfo.builder().fieldName("location").description("地点").required(true).build(),
                FieldInfo.builder().fieldName("date").description("时间").required(true).build(),
                FieldInfo.builder().fieldName("weather").description("天气").required(true).build(),
                FieldInfo.builder().fieldName("temperature").description("温度").required(true).build()));
        config.setWithChatHistory(withChatHistory);
        config.setMaxResponse(10);
        return config;
    }

    private static PromptTemplate defaultPrompt() {
        return PromptTemplate.builder().content(QuestionerDefaultConfig.getDefaultTemplate("zh")).build();
    }

    private static Model modelReturning(String... responses) throws Exception {
        Model model = mock(Model.class);
        Deque<String> contents = new ArrayDeque<>(List.of(responses));
        when(model.invoke(any(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull())).thenAnswer(invocation -> new AssistantMessage(contents.removeFirst()));
        return model;
    }

    private static ModelContext contextBackedBy(List<BaseMessage> messages) {
        ModelContext context = mock(ModelContext.class);
        when(context.addMessages(any(BaseMessage.class))).thenAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return List.copyOf(messages);
        });
        when(context.getContextWindow(null, null, null, 5)).thenAnswer(invocation ->
                ContextWindow.builder().contextMessages(List.copyOf(messages)).build());
        return context;
    }
}
