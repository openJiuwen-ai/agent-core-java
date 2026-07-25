/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimpleApiWrapperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void callExecutesRegisteredFunction() throws Exception {
        SimpleApiWrapper wrapper = new SimpleApiWrapper("echo", Map.of(
                "echo", (Function<Map<String, Object>, Object>) params -> params.get("value")
        ));

        Object[] result = wrapper.call(Map.of("name", "echo"), Map.of("value", "ok"));

        assertEquals(0, result[1]);
        assertTrue(String.valueOf(result[0]).contains("\"response\":\"ok\""));
    }

    @Test
    void simpleApiWrapperFromCallableRegistersNamedFunction() {
        SimpleApiWrapperFromCallable wrapper = new SimpleApiWrapperFromCallable(
                (Function<Map<String, Object>, Object>) params -> params.getOrDefault("n", 0),
                "callable",
                Map.of()
        );

        Object[] result = wrapper.call(Map.of("name", "callable"), Map.of("n", 7));

        assertEquals(0, result[1]);
        assertTrue(String.valueOf(result[0]).contains("\"response\":7"));
    }

    @Test
    void constructorLoadsFunctionsThroughToolModuleLoader() throws Exception {
        Path toolPath = tempDir.resolve("tool.py");
        Files.writeString(toolPath, "# placeholder");

        SimpleApiWrapper wrapper = new SimpleApiWrapper(
                toolPath.toString(),
                "loaded",
                Map.of(),
                loadedPath -> new SimpleApiWrapper.LoadedToolModule(
                        "module:" + loadedPath,
                        Map.of("loaded", (Function<Map<String, Object>, Object>) params -> params.get("x"))
                )
        );

        Object[] result = wrapper.call(Map.of("name", "loaded"), Map.of("x", "from-loader"));

        assertEquals(0, result[1]);
        assertTrue(String.valueOf(result[0]).contains("\"response\":\"from-loader\""));
    }

    @Test
    void loadCustomDataSupportsJsonAndJsonlShapes() throws Exception {
        Path jsonl = tempDir.resolve("tools.jsonl");
        Files.writeString(jsonl, """
                {"function":{"name":"fn_jsonl","description":"demo"}}
                {"function":[{"name":"fn_jsonl_2"}]}
                """);
        Path json = tempDir.resolve("tools.json");
        Files.writeString(json, """
                {"functions":[{"name":"fn_json","description":"demo"}]}
                """);

        List<Map<String, Object>> jsonlTools = SimpleApiWrapper.loadCustomData(jsonl.toString(), null);
        List<Map<String, Object>> jsonTools = SimpleApiWrapper.loadCustomData(json.toString(), null);

        assertEquals(2, jsonlTools.size());
        assertEquals(1, jsonTools.size());
        assertEquals("function", jsonlTools.get(0).get("type"));
        assertEquals("fn_json", ((Map<?, ?>) jsonTools.get(0).get("function")).get("name"));
    }

    @Test
    void missingFunctionReturnsPythonCompatibleErrorShape() throws Exception {
        SimpleApiWrapper wrapper = new SimpleApiWrapper("missing", Map.of());

        Object[] result = wrapper.call(Map.of("name", "missing"), Map.of());
        Map<?, ?> payload = OBJECT_MAPPER.readValue(String.valueOf(result[0]), Map.class);

        assertEquals(12, result[1]);
        assertEquals("", payload.get("response"));
        assertNotNull(payload.get("error"));
    }
}
