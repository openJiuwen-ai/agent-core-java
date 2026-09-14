/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.DLGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * <p>Mirrors Python's {@code TestDLGeneratorInit} and {@code TestDLGeneratorTemplates} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_dl_generator.py}.</p>
 */
class DLGeneratorMissingTest {

    @Test
    void initSuccess() throws ReflectiveOperationException {
        Model mockModel = modelReturning("unused");

        DLGenerator generator = new DLGenerator(mockModel);

        assertSame(mockModel, fieldValue(generator, "llm"));
    }

    @Test
    void initWithNoneLlm() throws ReflectiveOperationException {
        DLGenerator generator = new DLGenerator(null);

        assertNull(fieldValue(generator, "llm"));
    }

    @Test
    void generateSystemTemplateExists() {
        assertNotNull(DLGenerator.DL_GENERATE_SYSTEM_TEMPLATE);
    }

    @Test
    void refineUserTemplateExists() {
        assertNotNull(DLGenerator.DL_REFINE_USER_TEMPLATE);
    }

    @Test
    void generateSystemTemplateFormat() {
        List<BaseMessage> messages = DLGenerator.DL_GENERATE_SYSTEM_TEMPLATE.format(Map.of(
                "components", "test components",
                "schema", "test schema",
                "plugins", "test plugins",
                "examples", "test examples"
        )).toMessages();

        assertFalse(messages.isEmpty());
    }

    @Test
    void refineUserTemplateFormat() {
        List<BaseMessage> messages = DLGenerator.DL_REFINE_USER_TEMPLATE.format(Map.of(
                "user_input", "test input",
                "exist_mermaid", "test mermaid",
                "exist_dl", "test dl"
        )).toMessages();

        assertFalse(messages.isEmpty());
    }

    private static Model modelReturning(String content) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(content)));
    }

    private static Object fieldValue(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
