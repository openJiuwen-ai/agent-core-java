/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.store.graph.Entity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for graph-memory utils.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.utils} module in
 * {@code openjiuwen/core/memory/graph/graph_memory/utils.py}.</p>
 *
 * <p>Mirrors Python's {@code TestMsg2dict}, {@code TestUpdateEntity}, and
 * {@code TestAssembleInvokeParams} in
 * {@code tests/unit_tests/core/memory/graph/graph_memory/test_utils.py}.</p>
 */
class GraphMemoryUtilsTest {

    @Test
    void msgToDictConvertsMessagesAndLeavesDictMessages() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("role", "system");
        raw.put("content", "raw");
        UserMessage userMessage = new UserMessage("hello");

        List<Map<String, Object>> result = GraphMemoryUtils.msgToDict(List.of(raw, userMessage));

        assertThat(result).containsExactly(
                raw,
                Map.of("role", "user", "content", "hello")
        );
    }

    @Test
    void msgToDictPreservesModelMetadataWhenRequested() {
        BaseMessage message = BaseMessage.builder()
                .role("assistant")
                .content("answer")
                .name("assistant-name")
                .metadata(Map.of("trace_id", "abc"))
                .build();

        List<Map<String, Object>> result = GraphMemoryUtils.msgToDict(List.of(message), true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("role", "assistant")
                .containsEntry("content", "answer")
                .containsEntry("name", "assistant-name")
                .containsEntry("metadata", Map.of("trace_id", "abc"));
    }

    @Test
    void msgToDictConvertsSingleBaseMessage() {
        List<Map<String, Object>> result = GraphMemoryUtils.msgToDict(List.of(new UserMessage("hello")));

        assertThat(result).containsExactly(Map.of("role", "user", "content", "hello"));
    }

    @Test
    void msgToDictRejectsInvalidInput() {
        assertThatThrownBy(() -> GraphMemoryUtils.msgToDict(List.of("bad")))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("graph memory");
    }

    @Test
    void msgToDictRejectsNonListInput() {
        assertThatThrownBy(() -> GraphMemoryUtils.msgToDict("single message"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("not a list");
    }

    @Test
    void updateEntitySetsSummaryFromJson() {
        Entity entity = new Entity();
        entity.setContent("");

        GraphMemoryUtils.updateEntity(entity, "{\"summary\":\"A short summary.\"}", Map.of("type", "object"));

        assertThat(entity.getContent()).isEqualTo("A short summary.");
    }

    @Test
    void updateEntitySetsAttributesFromJsonObject() {
        Entity entity = new Entity();

        GraphMemoryUtils.updateEntity(
                entity,
                "{\"summary\":\"\",\"attributes\":{\"key\":\"value\"}}",
                Map.of("type", "object", "properties", Map.of("summary", Map.of(), "attributes", Map.of()))
        );

        assertThat(entity.getAttributes()).containsExactlyEntriesOf(Map.of("key", "value"));
    }

    @Test
    void updateEntityParsesSummaryListAndAttributeJsonString() {
        Entity entity = new Entity();

        GraphMemoryUtils.updateEntity(
                entity,
                "{\"summary\":[\" first \",\"second\"],\"attributes\":\"{\\\"age\\\":3}\"}",
                null
        );

        assertThat(entity.getContent()).isEqualTo("first\nsecond");
        assertThat(entity.getAttributes()).containsEntry("age", 3);
    }

    @Test
    void updateEntityIgnoresNullLikeSummaryAndBadAttributes() {
        Entity entity = new Entity();
        entity.setContent("original");
        entity.setAttributes(Map.of("kept", true));

        GraphMemoryUtils.updateEntity(
                entity,
                "{\"summary\":\"none value\",\"attributes\":42}",
                null
        );

        assertThat(entity.getContent()).isEqualTo("original");
        assertThat(entity.getAttributes()).containsEntry("kept", true);
    }

    @Test
    void parseSummaryUsesStringSummary() throws Exception {
        Entity entity = new Entity();

        parseSummary(entity, Map.of("summary", "just a string summary"));

        assertThat(entity.getContent()).isEqualTo("just a string summary");
    }

    @Test
    void parseSummaryJoinsSetSummary() throws Exception {
        Entity entity = new Entity();

        parseSummary(entity, Map.of("summary", new LinkedHashSet<>(List.of("a", "b"))));

        assertThat(entity.getContent().split("\\n")).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void updateEntityIgnoresInvalidAttributeList() {
        Entity entity = new Entity();
        entity.setAttributes(Map.of("orig", 1));

        GraphMemoryUtils.updateEntity(
                entity,
                "{\"summary\":\"\",\"attributes\":[1,2,3]}",
                Map.of("type", "object", "properties", Map.of("summary", Map.of(), "attributes", Map.of()))
        );

        assertThat(entity.getAttributes()).containsEntry("orig", 1);
    }

    @Test
    void updateEntityUsesFirstParsedListElement() {
        Entity entity = new Entity();

        GraphMemoryUtils.updateEntity(entity, "[{\"summary\":\"first\"}]", Map.of("type", "object"));

        assertThat(entity.getContent()).isEqualTo("first");
    }

    @Test
    void updateEntityWrapsStringParseResultAsSummary() {
        Entity entity = new Entity();

        GraphMemoryUtils.updateEntity(entity, "\"string summary\"", Map.of("type", "string"));

        assertThat(entity.getContent()).isEqualTo("string summary");
    }

    @Test
    void parseSummaryConvertsNonStringSummary() throws Exception {
        Entity entity = new Entity();

        parseSummary(entity, Map.of("summary", 42));

        assertThat(entity.getContent()).isEqualTo("42");
    }

    @Test
    void updateEntityConvertsAttributePairs() {
        Entity entity = new Entity();

        GraphMemoryUtils.updateEntity(
                entity,
                "{\"summary\":\"useful\",\"attributes\":[[\"level\",\"gold\"],[\"score\",9]]}",
                null
        );

        assertThat(entity.getContent()).isEqualTo("useful");
        assertThat(entity.getAttributes()).containsEntry("level", "gold")
                .containsEntry("score", 9);
    }

    @Test
    void assembleInvokeParamsFormatsPromptAndAddsOutputModelWhenPresent() {
        PromptTemplate template = PromptTemplate.builder()
                .content(List.of(new UserMessage("Hello {{name}}")))
                .build();

        Map<String, Object> params = GraphMemoryUtils.assembleInvokeParams(
                Map.of("name", "Alice"),
                template,
                Map.of("type", "json_object")
        );

        assertThat(params).containsEntry("response_format", Map.of("type", "json_object"));
        assertThat(params.get("messages")).isEqualTo(List.of(Map.of("role", "user", "content", "Hello Alice")));
    }

    @Test
    void assembleInvokeParamsUsesOnlyMessagesWithoutOutputModel() {
        PromptTemplate template = PromptTemplate.builder()
                .content(List.of(new UserMessage("Hello")))
                .build();

        Map<String, Object> params = GraphMemoryUtils.assembleInvokeParams(Map.of("k", "v"), template, null);

        assertThat(params).containsEntry("messages", List.of(Map.of("role", "user", "content", "Hello")));
        assertThat(params).doesNotContainKey("response_format");
    }

    @SuppressWarnings("unchecked")
    private static void parseSummary(Entity entity, Map<String, Object> extractedEntityInfo) throws Exception {
        Method method = GraphMemoryUtils.class.getDeclaredMethod("parseSummary", Entity.class, Map.class);
        method.setAccessible(true);
        method.invoke(null, entity, extractedEntityInfo);
    }
}
