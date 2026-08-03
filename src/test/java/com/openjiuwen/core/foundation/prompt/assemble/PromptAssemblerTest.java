/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt.assemble;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.assemble.variables.TextableVariable;
import com.openjiuwen.core.foundation.prompt.assemble.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused parity tests for prompt assembly.
 *
 * <p>Mirrors Python's {@code PromptAssembler} in
 * {@code openjiuwen/core/foundation/prompt/assemble/assembler.py}.</p>
 */
class PromptAssemblerTest {

    @Test
    void assemblesStringTemplateAndExposesInputKeys() {
        PromptAssembler assembler = new PromptAssembler("Hello {{name}}", "{{", "}}");

        assertIterableEquals(List.of("name"), assembler.getInputKeys());
        assertEquals("Hello Alice", assembler.promptAssemble(Map.of("name", "Alice")));
    }

    @Test
    void keepsMissingPromptAssembleValuesAsPlaceholders() {
        PromptAssembler assembler = new PromptAssembler("Hello {{name}}", "{{", "}}");

        assertEquals("Hello {{name}}", assembler.promptAssemble(Map.of()));
    }

    @Test
    void rejectsVariablesNotDeclaredInTemplate() {
        Map<String, Variable> variables = new LinkedHashMap<>();
        variables.put("other", new TextableVariable("value", "other"));

        BaseError error = assertThrows(BaseError.class,
                () -> new PromptAssembler("Hello {{name}}", "{{", "}}", variables));

        assertEquals(StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED, error.getStatus());
    }

    @Test
    void assemblesMessageContentInPlace() {
        UserMessage message = new UserMessage("Hello {{name}}");
        List<BaseMessage> messages = new ArrayList<>();
        messages.add(message);
        PromptAssembler assembler = new PromptAssembler(messages, "{{", "}}");

        Object result = assembler.promptAssemble(Map.of("name", "Bob"));

        assertSame(messages, result);
        assertEquals("Hello Bob", message.getContent());
    }
}
