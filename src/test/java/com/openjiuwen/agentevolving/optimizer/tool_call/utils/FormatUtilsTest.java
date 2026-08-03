package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatUtilsTest {

    @Test
    void parseJsonFindsHeaderScopedObject() {
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) FormatUtils.parseJson(
                "noise {\"answer\": \"ok\", \"x\": 1} tail",
                "answer"
        );

        assertEquals("ok", parsed.get("answer"));
        assertEquals(1, ((Number) parsed.get("x")).intValue());
    }

    @Test
    void parseJsonFallsBackToPythonLiteralSyntax() {
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) FormatUtils.parseJson(
                "{'answer': 'ok', 'enabled': True, 'value': None}"
        );

        assertEquals("ok", parsed.get("answer"));
        assertEquals(Boolean.TRUE, parsed.get("enabled"));
        assertEquals(null, parsed.get("value"));
    }

    @Test
    void formatPromptLlamaConcatenatesNullSafely() {
        assertEquals("systemuser", FormatUtils.formatPromptLlama("system", "user"));
        assertEquals("user", FormatUtils.formatPromptLlama(null, "user"));
        assertEquals("system", FormatUtils.formatPromptLlama("system", null));
    }
}
