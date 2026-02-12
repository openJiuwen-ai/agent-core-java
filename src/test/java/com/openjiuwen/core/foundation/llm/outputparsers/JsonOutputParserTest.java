// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.outputparsers;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON输出解析器测试
 */
class JsonOutputParserTest {

    private JsonOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonOutputParser();
    }

    @Test
    @DisplayName("测试解析有效的JSON字符串")
    void testParseValidJsonString() throws Exception {
        String jsonStr = "{\"name\": \"test\", \"value\": 123}";
        Object result = parser.parse(jsonStr).get();
        
        assertNotNull(result);
        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("test", map.get("name"));
        assertEquals(123, ((Number) map.get("value")).intValue());
    }

    @Test
    @DisplayName("测试解析Markdown代码块中的JSON")
    void testParseValidJsonInMarkdown() throws Exception {
        String markdownJson = "Here is some info:\n```json\n{\"item\": \"apple\", \"price\": 1.5}\n```\nThanks!";
        Object result = parser.parse(markdownJson).get();
        
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("apple", map.get("item"));
        assertEquals(1.5, ((Number) map.get("price")).doubleValue());
    }

    @Test
    @DisplayName("测试解析AssistantMessage对象中的JSON")
    void testParseValidJsonInAssistantMessage() throws Exception {
        AssistantMessage aiMessage = new AssistantMessage("```json\n{\"status\": \"success\", \"code\": 200}\n```");
        Object result = parser.parse(aiMessage).get();
        
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals("success", map.get("status"));
        assertEquals(200, ((Number) map.get("code")).intValue());
    }

    @Test
    @DisplayName("测试解析无效的JSON字符串")
    void testParseInvalidJsonString() throws Exception {
        String invalidJson = "{\"name\": \"test\", \"value\": 123,";
        Object result = parser.parse(invalidJson).get();
        assertNull(result);
    }

    @Test
    @DisplayName("测试解析非JSON文本")
    void testParseNonJsonString() throws Exception {
        String nonJson = "This is just plain text.";
        Object result = parser.parse(nonJson).get();
        assertNull(result);
    }

    @Test
    @DisplayName("测试解析空字符串")
    void testParseEmptyString() throws Exception {
        Object result = parser.parse("").get();
        assertNull(result);
    }

    @Test
    @DisplayName("测试解析null输入")
    void testParseNullInput() throws Exception {
        Object result = parser.parse((String) null).get();
        assertNull(result);
    }

    @Test
    @DisplayName("测试解析复杂的JSON结构")
    void testParseComplexJson() throws Exception {
        String complexJson = """
            ```json
            {
                "users": [
                    {"id": 1, "name": "Alice", "active": true},
                    {"id": 2, "name": "Bob", "active": false}
                ],
                "metadata": {
                    "total": 2,
                    "page": 1
                }
            }
            ```""";
        Object result = parser.parse(complexJson).get();
        
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertTrue(map.containsKey("users"));
        assertTrue(map.containsKey("metadata"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) map.get("users");
        assertEquals(2, users.size());
        assertEquals("Alice", users.get(0).get("name"));
    }

    // ====== 流式解析测试 ======

    @Test
    @DisplayName("测试流式解析有效的JSON块")
    void testStreamParseValidJsonChunks() {
        List<String> chunks = List.of(
                "```json\n",
                "{\"data\": ",
                "\"value\"}\n",
                "```"
        );
        
        List<Object> parsedObjects = new ArrayList<>();
        Iterator<Object> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        assertEquals(1, parsedObjects.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) parsedObjects.get(0);
        assertEquals("value", result.get("data"));
    }

    @Test
    @DisplayName("测试流式解析分片的JSON块")
    void testStreamParseFragmentedJsonChunks() {
        List<String> chunks = List.of(
                "Some text before.\n",
                "```json\n",
                "{\"id\": 1,",
                "\"name\": \"",
                "Fragmented Item\"",
                "}\n",
                "```\n",
                "More text after."
        );
        
        List<Object> parsedObjects = new ArrayList<>();
        Iterator<Object> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        assertEquals(1, parsedObjects.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) parsedObjects.get(0);
        assertEquals(1, ((Number) result.get("id")).intValue());
        assertEquals("Fragmented Item", result.get("name"));
    }

    @Test
    @DisplayName("测试流式解析多个JSON对象")
    void testStreamParseMultipleJsonObjects() {
        List<String> chunks = List.of(
                "```json\n{\"a\":1}\n```",
                "Some text.",
                "```json\n{\"b\":2}\n```"
        );
        
        List<Object> parsedObjects = new ArrayList<>();
        Iterator<Object> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        assertEquals(2, parsedObjects.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) parsedObjects.get(0);
        assertEquals(1, ((Number) first.get("a")).intValue());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> second = (Map<String, Object>) parsedObjects.get(1);
        assertEquals(2, ((Number) second.get("b")).intValue());
    }

    @Test
    @DisplayName("测试流式解析无效的JSON块")
    void testStreamParseInvalidJsonChunks() {
        List<String> chunks = List.of(
                "```json\n",
                "{\"data\": ",
                "\"value\" \n",  // 缺少闭合括号
                "```"
        );
        
        List<Object> parsedObjects = new ArrayList<>();
        Iterator<Object> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        assertEquals(0, parsedObjects.size());  // 无效JSON不应yield任何结果
    }

    @Test
    @DisplayName("测试流式解析混合内容和JSON")
    void testStreamParseMixedContentAndJson() {
        List<String> chunks = List.of(
                "Hello world. ",
                "```json\n{\"key\":",
                "\"value\"}\n```",
                " End of message."
        );
        
        List<Object> parsedObjects = new ArrayList<>();
        Iterator<Object> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        assertEquals(1, parsedObjects.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) parsedObjects.get(0);
        assertEquals("value", result.get("key"));
    }

    @Test
    @DisplayName("测试流式解析AssistantMessageChunk")
    void testStreamParseAssistantMessageChunks() {
        List<AssistantMessageChunk> chunks = List.of(
                new AssistantMessageChunk.Builder().content("```json\n{\"status\":").build(),
                new AssistantMessageChunk.Builder().content("\"ok\"}\n```").build()
        );
        
        List<Object> parsedObjects = new ArrayList<>();
        Iterator<Object> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        assertEquals(1, parsedObjects.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) parsedObjects.get(0);
        assertEquals("ok", result.get("status"));
    }

    @Test
    @DisplayName("测试流式解析不带Markdown的直接JSON")
    void testStreamParseDirectJsonWithoutMarkdown() {
        List<String> chunks = List.of(
                "{\"direct\":",
                "\"json\"}"
        );
        
        List<Object> parsedObjects = new ArrayList<>();
        Iterator<Object> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        assertEquals(1, parsedObjects.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) parsedObjects.get(0);
        assertEquals("json", result.get("direct"));
    }

    @Test
    @DisplayName("测试流式解析空块")
    void testStreamParseEmptyChunks() {
        // 使用Arrays.asList而不是List.of，因为List.of不允许null元素
        List<String> chunks = java.util.Arrays.asList("", null, "");
        
        List<Object> parsedObjects = new ArrayList<>();
        Iterator<Object> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        assertEquals(0, parsedObjects.size());
    }

    @Test
    @DisplayName("测试流式解析复杂JSON块")
    void testStreamParseComplexJsonChunks() {
        List<String> chunks = List.of(
                "```json\n{",
                "\"users\":[",
                "{\"id\":1,\"name\":\"Alice\"},",
                "{\"id\":2,\"name\":\"Bob\"}",
                "],\"total\":2",
                "}\n```"
        );
        
        List<Object> parsedObjects = new ArrayList<>();
        Iterator<Object> iterator = parser.streamParse(chunks.iterator());
        while (iterator.hasNext()) {
            parsedObjects.add(iterator.next());
        }

        assertEquals(1, parsedObjects.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) parsedObjects.get(0);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) result.get("users");
        assertEquals(2, users.size());
        assertEquals("Alice", users.get(0).get("name"));
        assertEquals("Bob", users.get(1).get("name"));
        assertEquals(2, ((Number) result.get("total")).intValue());
    }
}

