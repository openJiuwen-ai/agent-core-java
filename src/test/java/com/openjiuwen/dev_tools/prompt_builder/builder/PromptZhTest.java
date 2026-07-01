/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.prompt_zh} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/prompt_zh.py}.
 */
class PromptZhTest {
    @Test
    void exposesAllPythonPromptTemplatesInDeclarationOrder() {
        assertEquals(11, PromptZh.templates().size());
        assertEquals("PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE", PromptZh.templates().keySet().iterator().next());
    }

    @Test
    void messageTemplatesPreserveRolesAndContent() {
        PromptTemplate systemTemplate = PromptZh.PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE;
        BaseMessage systemMessage = systemTemplate.toMessages().get(0);
        assertEquals("system", systemMessage.getRole());
        assertTrue(systemMessage.getContentAsString().startsWith("\n"));
        assertTrue(systemMessage.getContentAsString().contains("markdown"));
        assertEquals(518, systemMessage.getContentAsString().length());

        PromptTemplate userTemplate = PromptZh.PROMPT_BUILD_GENERAL_META_USER_TEMPLATE;
        BaseMessage userMessage = userTemplate.toMessages().get(0);
        assertEquals("user", userMessage.getRole());
        assertTrue(userMessage.getContentAsString().contains("{{instruction}}"));
    }

    @Test
    void stringTemplateFormatsBadCasePlaceholdersLikePythonTemplate() {
        PromptTemplate formatted = PromptZh.FORMAT_BAD_CASE_TEMPLATE.format(Map.of(
                "question", "Q",
                "label", "A",
                "answer", "B",
                "reason", "R"
        ));
        assertEquals("\n[question]: Q\n[expected answer]: A\n[assistant answer]: B\n[reason]: R\n=== \n",
                formatted.getContent());
    }
}
