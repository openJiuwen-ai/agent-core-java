/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for prompt templates.
 *
 * <p>Mirrors Python's {@code PromptTemplate} in
 * {@code openjiuwen/core/foundation/prompt/template.py}.</p>
 */
class PromptTemplateTest {

    @Test
    void emptyContentConvertsToNoMessages() {
        PromptTemplate template = PromptTemplate.builder().content("").build();

        assertTrue(template.toMessages().isEmpty());
    }

    @Test
    void stringContentWrapsAsUserMessage() {
        PromptTemplate template = PromptTemplate.builder().content("hello").build();

        List<BaseMessage> messages = template.toMessages();

        assertEquals(1, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("user", messages.get(0).getRole());
        assertEquals("hello", messages.get(0).getContent());
    }

    @Test
    void messageListIsDeepCopiedByToMessages() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tags", new ArrayList<>(List.of("source")));
        UserMessage original = UserMessage.builder()
                .content("hello")
                .metadata(metadata)
                .build();
        PromptTemplate template = PromptTemplate.builder().content(List.of(original)).build();

        List<BaseMessage> messages = template.toMessages();

        assertEquals(1, messages.size());
        assertNotSame(original, messages.get(0));
        assertNotSame(original.getMetadata(), messages.get(0).getMetadata());

        @SuppressWarnings("unchecked")
        List<String> copiedTags = (List<String>) messages.get(0).getMetadata().get("tags");
        copiedTags.add("copy");

        @SuppressWarnings("unchecked")
        List<String> originalTags = (List<String>) original.getMetadata().get("tags");
        assertEquals(List.of("source"), originalTags);
        assertEquals(List.of("source", "copy"), copiedTags);
    }

    @Test
    void rejectsNonMessageListItems() {
        PromptTemplate template = PromptTemplate.builder().content(List.of("bad")).build();

        BaseError error = assertThrows(BaseError.class, template::toMessages);

        assertEquals(StatusCode.PROMPT_TEMPLATE_INVALID, error.getStatus());
    }

    @Test
    void emptyKeywordsReturnDeepCopiedTemplate() {
        UserMessage original = UserMessage.builder()
                .content("hello {{name}}")
                .metadata(new LinkedHashMap<>(Map.of("origin", new ArrayList<>(List.of("python")))))
                .build();
        PromptTemplate template = PromptTemplate.builder()
                .name("greeting")
                .content(List.of(original))
                .build();

        PromptTemplate formatted = template.format(Map.of());

        assertEquals("greeting", formatted.getName());
        assertNotSame(template, formatted);
        assertNotSame(template.getContent(), formatted.getContent());

        @SuppressWarnings("unchecked")
        List<BaseMessage> formattedMessages = (List<BaseMessage>) formatted.getContent();
        assertNotSame(original, formattedMessages.get(0));
        assertEquals("hello {{name}}", formattedMessages.get(0).getContent());
    }

    @Test
    void formatReplacesKnownPlaceholdersOnly() {
        PromptTemplate template = PromptTemplate.builder()
                .name("greeting")
                .content("Hello {{name}}, {{missing}}")
                .build();

        PromptTemplate formatted = template.format(Map.of("name", "Ada", "unused", "ignored"));

        assertEquals("greeting", formatted.getName());
        assertEquals("Hello Ada, {{missing}}", formatted.getContent());
        assertEquals("Hello {{name}}, {{missing}}", template.getContent());
    }

    @Test
    void messageFormatReturnsCopiedMessages() {
        UserMessage original = new UserMessage("Hello {{name}}");
        List<BaseMessage> messages = new ArrayList<>();
        messages.add(original);
        PromptTemplate template = PromptTemplate.builder().content(messages).build();

        PromptTemplate formatted = template.format(Map.of("name", "Lin"));

        assertSame(messages, template.getContent());
        assertEquals("Hello {{name}}", original.getContent());

        @SuppressWarnings("unchecked")
        List<BaseMessage> formattedMessages = (List<BaseMessage>) formatted.getContent();
        assertFalse(formattedMessages.isEmpty());
        assertNotSame(original, formattedMessages.get(0));
        assertEquals("Hello Lin", formattedMessages.get(0).getContent());
    }
}
