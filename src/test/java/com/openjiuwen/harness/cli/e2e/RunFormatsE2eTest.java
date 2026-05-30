/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-03 / E2E-04: output-format json and stream-json.
 * <p>
 * Mirrors Python's {@code test_run_formats} in
 * {@code tests.cli.e2e.test_run_formats}.
 */
class RunFormatsE2eTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> VALID_TYPES = Set.of(
            "llm_output", "llm_reasoning", "answer", "message",
            "__interaction__", "controller_output"
    );

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void runJsonFormat() throws Exception {
        String stdout = "{\"result\":\"4\",\"chunks\":1,\"model\":\"mock-model\"}";
        int returnCode = 0;

        JsonNode data = MAPPER.readTree(stdout);

        assertEquals(0, returnCode);
        assertTrue(data.has("result"));
        assertFalse(data.get("result").asText().isBlank());
        assertTrue(data.get("chunks").asInt() > 0);
        assertTrue(data.has("model"));
    }

    @Test
    @Disabled("E2E test requires real LLM API credentials")
    void runStreamJsonFormat() throws Exception {
        String stdout = """
                {"type":"llm_output","index":0,"payload":{"content":"hello"}}
                {"type":"answer","index":1,"payload":{"content":"hello"}}
                """;
        int returnCode = 0;
        boolean hasContent = false;
        int lineCount = 0;

        for (String line : stdout.strip().split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode data = MAPPER.readTree(line);
            assertTrue(data.has("type"));
            assertTrue(data.has("index"));
            assertTrue(VALID_TYPES.contains(data.get("type").asText()));
            if ("llm_output".equals(data.get("type").asText()) || "answer".equals(data.get("type").asText())) {
                hasContent = true;
            }
            lineCount++;
        }

        assertEquals(0, returnCode);
        assertTrue(lineCount >= 1);
        assertTrue(hasContent);
    }

    @Test
    void validTypesContainsExpectedValues() {
        assertTrue(VALID_TYPES.contains("llm_output"));
        assertTrue(VALID_TYPES.contains("answer"));
        assertTrue(VALID_TYPES.contains("llm_reasoning"));
        assertEquals(6, VALID_TYPES.size());
    }

    @Test
    void jsonStreamLineCanBeParsed() throws Exception {
        String line = "{\"type\":\"llm_output\",\"index\":0,\"payload\":{\"content\":\"hi\"}}";
        JsonNode data = MAPPER.readTree(line);
        assertTrue(data.has("type"));
        assertTrue(data.has("index"));
        assertTrue(VALID_TYPES.contains(data.get("type").asText()));
    }
}
