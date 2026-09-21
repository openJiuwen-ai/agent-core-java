/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Protocol tests for {@link AgentHint}, mirroring the validation fixed by
 * Python {@code openai_model_client} kv-cache helpers.
 */
class AgentHintTest {
    /** Grouped range bounds keeping helper signatures within five parameters. */
    private record EditRanges(Integer msgStart, Integer msgEnd, Integer toolsStart, Integer toolsEnd) {
        static EditRanges none() {
            return new EditRanges(null, null, null, null);
        }

        static EditRanges messages(Integer start, Integer end) {
            return new EditRanges(start, end, null, null);
        }

        static EditRanges tools(Integer start, Integer end) {
            return new EditRanges(null, null, start, end);
        }

        static EditRanges messagesAndTools(Integer msgStart, Integer msgEnd,
                Integer toolsStart, Integer toolsEnd) {
            return new EditRanges(msgStart, msgEnd, toolsStart, toolsEnd);
        }
    }

    private static AgentHint.KvCacheRange range(String target, EditRanges ranges, boolean shouldIncludeTools) {
        return new AgentHint.KvCacheRange(target, ranges.msgStart(), ranges.msgEnd(),
                ranges.toolsStart(), ranges.toolsEnd(), shouldIncludeTools);
    }

    @Test
    void identityHintRequiresBothIds() {
        Map<String, Object> hint = AgentHint.identityHint("s-123", "root-1");

        assertThat(hint).containsEntry("session_id", "s-123").containsEntry("parent_session_id", "root-1");
        assertThatThrownBy(() -> AgentHint.identityHint(null, "root-1"))
                .hasMessageContaining("session_id is required");
        assertThatThrownBy(() -> AgentHint.identityHint("s-123", " "))
                .hasMessageContaining("parent_session_id is required");
    }

    @Test
    void sessionManageHintRejectsRangesAndIncludeTools() {
        assertThat(AgentHint.buildKvTargetEdits("offload", range("session", EditRanges.none(), false)))
                .singleElement()
                .satisfies(edit -> {
                    assertThat(edit).containsEntry("type", "offload").containsEntry("target", "session");
                });

        assertThatThrownBy(() ->
                AgentHint.buildKvTargetEdits("offload", range("session", EditRanges.messages(0, 3), false)))
                .hasMessageContaining("target=session does not accept message/tool ranges");
        assertThatThrownBy(() ->
                AgentHint.buildKvTargetEdits("offload", range("session", EditRanges.none(), true)))
                .hasMessageContaining("target=session does not accept include_tools=True");
    }

    @Test
    void messagesRangeEditValidatesHalfOpenRange() {
        List<Map<String, Object>> edits = AgentHint.buildKvTargetEdits(
                "evict", range("messages", EditRanges.messages(2, 5), false));

        assertThat(edits).hasSize(1);
        assertThat(edits.get(0)).containsEntry("start", 2).containsEntry("end", 5);

        assertThatThrownBy(() ->
                AgentHint.buildKvTargetEdits("evict", range("messages", EditRanges.messages(5, 2), false)))
                .hasMessageContaining("half-open range requires start < end");
        assertThatThrownBy(() ->
                AgentHint.buildKvTargetEdits("evict", range("messages", EditRanges.messages(-1, 2), false)))
                .hasMessageContaining("range must be non-negative");
        assertThatThrownBy(() ->
                AgentHint.buildKvTargetEdits("evict", range("messages", EditRanges.messages(2, null), false)))
                .hasMessageContaining("requires both start and end");
        assertThatThrownBy(() ->
                AgentHint.buildKvTargetEdits("evict",
                        range("messages", EditRanges.messagesAndTools(2, 5, 1, 2), false)))
                .hasMessageContaining("tools range requires include_tools=True or target=tools");
    }

    @Test
    void includeToolsAppendsToolsEdit() {
        List<Map<String, Object>> edits = AgentHint.buildKvTargetEdits(
                "evict", range("messages", EditRanges.messagesAndTools(2, 5, 1, 3), true));

        assertThat(edits).hasSize(2);
        assertThat(edits.get(1)).containsEntry("target", "tools").containsEntry("start", 1).containsEntry("end", 3);

        assertThatThrownBy(() ->
                AgentHint.buildKvTargetEdits("evict", range("tools", EditRanges.tools(1, 3), true)))
                .hasMessageContaining("target=tools should not also set include_tools=True");
        assertThatThrownBy(() ->
                AgentHint.buildKvTargetEdits("evict",
                        range("tools", EditRanges.messagesAndTools(0, 2, 1, 3), false)))
                .hasMessageContaining("messages range is invalid for target=tools");
    }

    @Test
    void unsupportedActionOrTargetFailsClosed() {
        assertThatThrownBy(() ->
                AgentHint.buildKvTargetEdits("delete", range("session", EditRanges.none(), false)))
                .hasMessageContaining("unsupported KV affinity action");
        assertThatThrownBy(() ->
                AgentHint.buildKvTargetEdits("evict", range("cache", EditRanges.none(), false)))
                .hasMessageContaining("unsupported KV affinity target");
    }

    @Test
    void managementHintCarriesContextManagementEdits() {
        Map<String, Object> hint = AgentHint.managementHint(
                "prefetch", "s-1", "root-1", range("session", EditRanges.none(), false));

        assertThat(hint).containsEntry("session_id", "s-1").containsEntry("parent_session_id", "root-1");
        assertThat(hint.get("context_management")).isInstanceOf(Map.class);
    }

    @Test
    void invokeKwargsFallbackToSelfParent() {
        Map<String, Object> kwargs = AgentHint.buildInvokeKwargs("s-1", null);

        assertThat(kwargs).containsEntry("session_id", "s-1").containsEntry("parent_session_id", "s-1");
        assertThatThrownBy(() -> AgentHint.buildInvokeKwargs("", null))
                .hasMessageContaining("session_id is required");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sanitizeToolCallsNormalizesAssistantToolCalls() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "demo");
        function.put("arguments", "{}");
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", "call-1");
        toolCall.put("index", 0);
        toolCall.put("function", function);
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("tool_calls", new ArrayList<>(List.of(toolCall)));
        List<Map<String, Object>> messages = new ArrayList<>(List.of(assistant));

        List<Map<String, Object>> sanitized = AgentHint.sanitizeToolCalls(messages);

        Object cleaned = sanitized.get(0).get("tool_calls");
        assertThat(cleaned).isInstanceOf(List.class);
        Map<String, Object> cleanedCall = ((List<Map<String, Object>>) cleaned).get(0);
        assertThat(cleanedCall).containsEntry("id", "call-1").containsEntry("type", "function");
        assertThat(cleanedCall.get("function")).isEqualTo(function);
    }

    @Test
    void lineageResolutionFallsBackToSelf() {
        KVCacheMetadata.Lineage lineage = KVCacheMetadata.resolveSessionLineage(null);

        assertThat(lineage.isPresent()).isFalse();
    }

    @Test
    void teamMemberIdentityIsStable() {
        String identity = KVCacheMetadata.teamMemberCacheIdentity("s-1", "team-1", "m-1");

        assertThat(identity).isEqualTo("team:s-1:team:team-1:member:m-1");
        assertThatThrownBy(() -> KVCacheMetadata.teamMemberCacheIdentity("s-1", "", "m-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compressorIdentityUsesStableSuffix() {
        KVCacheIdentity identity = KVCacheMetadata.contextCompressorCacheIdentity(
                "owner", "RoundLevelCompressor");

        assertThat(identity.cacheId()).isEqualTo("owner:compressor:round-level");
        assertThat(identity.parentCacheId()).isEqualTo("owner");
        assertThatThrownBy(() -> KVCacheMetadata.contextCompressorCacheIdentity("owner", "Unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void firstChangedIndexTreatsAppendOnlyAsUnchanged() {
        assertThat(KVCacheMetadata.firstChangedIndex(List.of("a", "b"), List.of("a", "b", "c"))).isEmpty();
        assertThat(KVCacheMetadata.firstChangedIndex(List.of("a", "b"), List.of("a", "x"))).hasValue(1);
        assertThat(KVCacheMetadata.firstChangedIndex(List.of("a", "b"), List.of("a"))).hasValue(1);
    }
}
