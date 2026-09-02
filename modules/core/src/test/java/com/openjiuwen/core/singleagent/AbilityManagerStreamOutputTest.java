// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.stream.OutputSchema;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for the unified {@code tool_output} stream chunk shape produced
 * by {@link AbilityManager#executeStream}. Covers {@code buildToolOutputChunk}
 * (payload shape, task_id null-safety, index propagation) and
 * {@code mergeStreamChunks} (string concatenation, empty list, mixed types).
 * <p>
 * These tests use reflection to invoke the private static helpers directly,
 * avoiding the heavier AgentSessionApi / StreamWriter / Runner bootstrap
 * needed by integration tests. The contract under test:
 * <pre>
 *   { "type": "tool_output", "index": chunkIndex,
 *     "payload": { "task_id", "tool_name", "tool_call_id", "content" } }
 * </pre>
 * No {@code session_id} field is emitted (it was intentionally removed).
 */
class AbilityManagerStreamOutputTest {

    private static final String TOOL_NAME = "web_search";
    private static final String TOOL_CALL_ID = "call_1_abc";

    private static ToolCall newToolCall() {
        return ToolCall.builder()
                .id(TOOL_CALL_ID)
                .name(TOOL_NAME)
                .arguments("{\"query\":\"test\"}")
                .index(0)
                .build();
    }

    private static OutputSchema invokeBuild(String taskId, Object content, int chunkIndex) throws Exception {
        Method m = AbilityManager.class.getDeclaredMethod(
                "buildToolOutputChunk", String.class, ToolCall.class, Object.class, int.class);
        m.setAccessible(true);
        return (OutputSchema) m.invoke(null, taskId, newToolCall(), content, chunkIndex);
    }

    @SuppressWarnings("unchecked")
    private static Object invokeMerge(List<Object> chunks) throws Exception {
        Method m = AbilityManager.class.getDeclaredMethod("mergeStreamChunks", List.class);
        m.setAccessible(true);
        return m.invoke(null, chunks);
    }

    // ========== buildToolOutputChunk ==========

    @Test
    void buildToolOutputChunk_normalCase_returnsUnifiedToolOutputShape() throws Exception {
        OutputSchema chunk = invokeBuild("deep_agent_task_s1_1", "找到 3 条结果", 2);

        assertThat(chunk.getType()).isEqualTo("tool_output");
        assertThat(chunk.getIndex()).isEqualTo(2);
        assertThat(chunk.getPayload()).isInstanceOf(Map.class);
        Map<String, Object> payload = (Map<String, Object>) chunk.getPayload();

        // 必须含这 4 个字段
        assertThat(payload).containsOnlyKeys("task_id", "tool_name", "tool_call_id", "content");
        assertThat(payload.get("task_id")).isEqualTo("deep_agent_task_s1_1");
        assertThat(payload.get("tool_name")).isEqualTo(TOOL_NAME);
        assertThat(payload.get("tool_call_id")).isEqualTo(TOOL_CALL_ID);
        assertThat(payload.get("content")).isEqualTo("找到 3 条结果");
    }

    @Test
    void buildToolOutputChunk_noSessionIdField_emitted() throws Exception {
        // 回归：session_id 字段已从 payload 中移除，下游不应再期望该字段
        OutputSchema chunk = invokeBuild("t1", "content", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) chunk.getPayload();
        assertThat(payload).doesNotContainKey("session_id");
    }

    @Test
    void buildToolOutputChunk_nullTaskId_writesEmptyString() throws Exception {
        // standalone ReActAgent 调用（无 task_id 注入）时 task_id 字段为空字符串，
        // 保证字段始终存在、类型稳定，下游不需要 null 判断
        OutputSchema chunk = invokeBuild(null, "content", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) chunk.getPayload();
        assertThat(payload.get("task_id")).isEqualTo("");
    }

    @Test
    void buildToolOutputChunk_indexPropagated() throws Exception {
        // chunkIndex 直接映射到 OutputSchema.index，用于下游按序重组流
        for (int i = 0; i < 5; i++) {
            OutputSchema chunk = invokeBuild("t1", "c" + i, i);
            assertThat(chunk.getIndex()).isEqualTo(i);
        }
    }

    @Test
    void buildToolOutputChunk_payloadIsInsertionOrdered() throws Exception {
        // LinkedHashMap 保证字段顺序：task_id → tool_name → tool_call_id → content
        OutputSchema chunk = invokeBuild("t1", "c", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) chunk.getPayload();
        assertThat(new ArrayList<>(payload.keySet()))
                .containsExactly("task_id", "tool_name", "tool_call_id", "content");
    }

    @Test
    void buildToolOutputChunk_nullContent_preservedAsNull() throws Exception {
        // content 字段原样透传，null 时不做转换（工具返回 null 的场景）
        OutputSchema chunk = invokeBuild("t1", null, 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) chunk.getPayload();
        assertThat(payload.get("content")).isNull();
    }

    // ========== mergeStreamChunks ==========

    @Test
    void mergeStreamChunks_allStrings_concatenated() throws Exception {
        // 典型流式工具场景：每个 chunk 是 String，合并为完整文本
        Object merged = invokeMerge(Arrays.asList("找到 3 条", "相关结果", ":\n"));
        assertThat(merged).isEqualTo("找到 3 条相关结果:\n");
    }

    @Test
    void mergeStreamChunks_emptyList_returnsEmptyString() throws Exception {
        // 工具 yield 0 个 chunk（如直接 return）时合并为空字符串，避免 null 污染 ToolMessage
        assertThat(invokeMerge(new ArrayList<>())).isEqualTo("");
        assertThat(invokeMerge(null)).isEqualTo("");
    }

    @Test
    void mergeStreamChunks_mixedTypes_returnsListCopy() throws Exception {
        // 非全 String（如结构化对象）时返回 ArrayList 包装，让 caller 看到每个 chunk
        Object obj = new Object();
        Object merged = invokeMerge(Arrays.asList("text", obj, 42));
        assertThat(merged).isInstanceOf(ArrayList.class);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) merged;
        assertThat(list).containsExactly("text", obj, 42);
        // 返回的是副本，修改不影响原入参
        list.add("extra");
        assertThat(list).hasSize(4);
    }

    @Test
    void mergeStreamChunks_nullStringElements_skipped() throws Exception {
        // String 列表里的 null 元素被跳过（不拼成 "null" 字符串）
        Object merged = invokeMerge(Arrays.asList("a", null, "b"));
        assertThat(merged).isEqualTo("ab");
    }

    @Test
    void mergeStreamChunks_singleString_returnedAsIs() throws Exception {
        // 单 chunk 的 String 直接返回（StringBuilder 拼接结果）
        assertThat(invokeMerge(List.of("only"))).isEqualTo("only");
    }
}
