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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for graph-memory utils.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.utils} module in
 * {@code openjiuwen/core/memory/graph/graph_memory/utils.py}.</p>
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
    void msgToDictRejectsInvalidInput() {
        assertThatThrownBy(() -> GraphMemoryUtils.msgToDict(List.of("bad")))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("graph memory");
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
}
