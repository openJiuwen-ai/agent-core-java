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
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.prompt_en} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/prompt_en.py}.
 */
class PromptEnTest {
    @Test
    void exposesAllPythonPromptTemplatesInDeclarationOrder() {
        assertEquals(11, PromptEn.templates().size());
        assertEquals("PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE", PromptEn.templates().keySet().iterator().next());
    }

    @Test
    void messageTemplatesPreserveRolesAndContent() {
        PromptTemplate systemTemplate = PromptEn.PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE;
        BaseMessage systemMessage = systemTemplate.toMessages().getFirst();
        assertEquals("system", systemMessage.getRole());
        assertTrue(systemMessage.getContentAsString().contains("Below is the meta-template in markdown format"));

        PromptTemplate userTemplate = PromptEn.PROMPT_BUILD_GENERAL_META_USER_TEMPLATE;
        BaseMessage userMessage = userTemplate.toMessages().getFirst();
        assertEquals("user", userMessage.getRole());
        assertTrue(userMessage.getContentAsString().contains("{{instruction}}"));
    }

    @Test
    void stringTemplateFormatsBadCasePlaceholdersLikePythonTemplate() {
        PromptTemplate formatted = PromptEn.FORMAT_BAD_CASE_TEMPLATE.format(Map.of(
                "question", "Q",
                "label", "A",
                "answer", "B",
                "reason", "R"
        ));
        assertEquals("\n[question]: Q\n[expected answer]: A\n[assistant answer]: B\n[reason]: R\n=== \n",
                formatted.getContent());
    }
}
