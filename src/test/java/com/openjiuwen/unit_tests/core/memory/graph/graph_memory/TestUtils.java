/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.graph.graph_memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.memory.graph.graph_memory.GraphMemoryUtils;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for graph_memory utils.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.memory.graph.graph_memory.test_utils}.
 */
class TestUtils {

    // ==================== TestMsg2dict ====================

    @Nested
    class TestMsg2dict {

        @Test
        @Tag("level0")
        void testListOfDictPassthrough() {
            /** List of dict with role and content is returned as-is */
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", "user");
            msg.put("content", "hi");
            messages.add(msg);

            List<Map<String, Object>> result = GraphMemoryUtils.msg2dict(messages);
            assertEquals(messages, result);
        }

        @Test
        @Tag("level0")
        void testListOfBaseMessageConverted() {
            /** List of BaseMessage is converted to role/content dicts */
            List<Object> messages = new ArrayList<>();
            messages.add(new UserMessage("hello"));

            List<Map<String, Object>> result = GraphMemoryUtils.msg2dict(messages);
            assertEquals(1, result.size());
            assertEquals("user", result.get(0).get("role"));
            assertEquals("hello", result.get(0).get("content"));
        }

        @Test
        @Tag("level0")
        void testPreserveMetaIncludesExtraFields() {
            /** With preserveMeta=true, full serialization is used for BaseMessage */
            List<Object> messages = new ArrayList<>();
            messages.add(new UserMessage("x"));

            List<Map<String, Object>> result = GraphMemoryUtils.msg2dict(messages, true);
            assertTrue(result.get(0) instanceof Map);
            assertTrue(result.get(0).containsKey("content"));
            assertTrue(result.get(0).containsKey("role"));
        }

        @Test
        @Tag("level0")
        void testNotListRaises() {
            /** Input that is not a list raises */
            assertThrows(BaseError.class, () -> {
                GraphMemoryUtils.msg2dict(null);
            });
        }

        @Test
        @Tag("level0")
        void testMixedTypeRaises() {
            /** List containing non-dict and non-BaseMessage raises */
            List<Object> messages = new ArrayList<>();
            messages.add(1);
            messages.add(2);

            assertThrows(BaseError.class, () -> {
                GraphMemoryUtils.msg2dict(messages);
            });
        }
    }

    // ==================== TestUpdateEntity ====================

    @Nested
    class TestUpdateEntity {

        @Test
        @Tag("level0")
        void testUpdateEntitySetsSummaryFromJson() {
            /** Valid JSON with summary updates entity content */
            Entity entity = new Entity("E1", "", "");
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");

            GraphMemoryUtils.updateEntity(entity, "{\"summary\": \"A short summary.\"}", schema);
            assertEquals("A short summary.", entity.getContent());
        }

        @Test
        @Tag("level0")
        void testUpdateEntityAttributes() {
            /** JSON with attributes sets entity.attributes */
            Entity entity = new Entity("E1", "", "", new HashMap<>());
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            properties.put("summary", new HashMap<>());
            properties.put("attributes", new HashMap<>());
            schema.put("properties", properties);

            GraphMemoryUtils.updateEntity(entity,
                    "{\"summary\": \"\", \"attributes\": {\"key\": \"value\"}}",
                    schema);
            assertEquals(Map.of("key", "value"), entity.getAttributes());
        }

        @Test
        @Tag("level0")
        void testUpdateEntityNullSummaryIgnored() {
            /** Summary that looks like null/empty is not applied */
            Entity entity = new Entity("E1", "", "original");
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");

            GraphMemoryUtils.updateEntity(entity, "{\"summary\": \"null\"}", schema);
            assertEquals("original", entity.getContent());
        }

        @Test
        @Tag("level0")
        void testUpdateEntityListSummaryJoined() {
            /** Summary as list is joined with newlines */
            Entity entity = new Entity("E1", "", "");
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");

            GraphMemoryUtils.updateEntity(entity, "{\"summary\": [\"a\", \"b\"]}", schema);
            assertEquals("a\nb", entity.getContent());
        }

        @Test
        @Tag("level0")
        void testUpdateEntityAttributesAsJsonString() {
            /** Attributes as JSON string are parsed and set */
            Entity entity = new Entity("E1", "", "", new HashMap<>());
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            properties.put("summary", new HashMap<>());
            properties.put("attributes", new HashMap<>());
            schema.put("properties", properties);

            GraphMemoryUtils.updateEntity(entity,
                    "{\"summary\": \"\", \"attributes\": \"{\\\"k\\\": \\\"v\\\"}\"}",
                    schema);
            assertEquals(Map.of("k", "v"), entity.getAttributes());
        }

        @Test
        @Tag("level0")
        void testUpdateEntityAttributesListFailsDictLogged() {
            /** Attributes as list that cannot convert to dict leaves attributes unchanged or empty */
            Entity entity = new Entity("E1", "", "", Map.of("orig", 1));
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            properties.put("summary", new HashMap<>());
            properties.put("attributes", new HashMap<>());
            schema.put("properties", properties);

            GraphMemoryUtils.updateEntity(entity,
                    "{\"summary\": \"\", \"attributes\": [1, 2, 3]}",
                    schema);
            // Cannot dict([1,2,3]) meaningfully; code catches and leaves attributes as {} or keeps orig
            assertTrue(entity.getAttributes() instanceof Map);
        }

        @Test
        @Tag("level0")
        void testUpdateEntityExtractedInfoAsStrTreatedAsSummary() throws Exception {
            Entity entity = new Entity("E1", "", "");

            invokeParseSummary(entity, Map.of("summary", "just a string summary"));

            assertEquals("just a string summary", entity.getContent());
        }

        @Test
        @Tag("level0")
        void testUpdateEntitySummaryAsSetJoined() throws Exception {
            Entity entity = new Entity("E1", "", "");

            invokeParseSummary(entity, Map.of("summary", Set.of("a", "b")));

            assertTrue(Set.of("a\nb", "b\na").contains(entity.getContent()));
        }

        @Test
        @Tag("level0")
        void testUpdateEntityExtractedInfoListTakesFirst() {
            Entity entity = new Entity("E1", "", "");

            GraphMemoryUtils.updateEntity(entity, "[{\"summary\": \"first\"}]", Map.of("type", "array"));

            assertEquals("first", entity.getContent());
        }

        @Test
        @Tag("level0")
        void testUpdateEntityExtractedInfoStrWrappedAsSummary() {
            Entity entity = new Entity("E1", "", "");

            GraphMemoryUtils.updateEntity(entity, "\"string summary\"", Map.of("type", "string"));

            assertEquals("string summary", entity.getContent());
        }

        @Test
        @Tag("level0")
        void testParseSummaryNonStrSummaryConverted() throws Exception {
            Entity entity = new Entity("E1", "", "");

            invokeParseSummary(entity, Map.of("summary", 42));

            assertEquals("42", entity.getContent());
        }
    }

    // ==================== TestAssembleInvokeParams ====================

    @Nested
    class TestAssembleInvokeParams {

        @Test
        @Tag("level0")
        void testAssembleInvokeParamsMessagesOnly() {
            /** Without output_model, params contain formatted messages from template */
            PromptTemplate template = new PromptTemplate("tmpl", "Hello {{name}}", "{{", "}}");

            Map<String, Object> params = GraphMemoryUtils.assembleInvokeParams(
                    Map.of("name", "World"), template, null);

            assertTrue(params.containsKey("messages"));
            assertEquals(List.of(Map.of("role", "user", "content", "Hello World")), params.get("messages"));
            assertFalse(params.containsKey("response_format"));
        }

        @Test
        @Tag("level0")
        void testAssembleInvokeParamsWithOutputModel() {
            /** With output_model, response_format is set */
            PromptTemplate template = new PromptTemplate("tmpl", List.of(new UserMessage("Hello")), "{{", "}}");
            Map<String, Object> output = new HashMap<>();
            output.put("type", "json_schema");
            Map<String, Object> jsonSchema = new HashMap<>();
            jsonSchema.put("name", "Test");
            output.put("json_schema", jsonSchema);

            Map<String, Object> params = GraphMemoryUtils.assembleInvokeParams(
                    Map.of("k", "v"), template, output);

            assertEquals(output, params.get("response_format"));
            assertEquals(List.of(Map.of("role", "user", "content", "Hello")), params.get("messages"));
        }
    }

    private static void invokeParseSummary(Entity entity, Map<String, Object> extractedEntityInfo) throws Exception {
        Method method = GraphMemoryUtils.class.getDeclaredMethod("parseSummary", Entity.class, Map.class);
        method.setAccessible(true);
        method.invoke(null, entity, extractedEntityInfo);
    }
}
