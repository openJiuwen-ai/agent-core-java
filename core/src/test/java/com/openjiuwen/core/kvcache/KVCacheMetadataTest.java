/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.session.AgentSession;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Compatibility tests for {@link KVCacheMetadata}, mirroring Python's
 * {@code tests/unit_tests/core/kv_cache/test_kv_cache_metadata.py}.
 */
class KVCacheMetadataTest {
    @Test
    void rangeIndicesAlwaysDescribeCompleteHalfOpenRanges() {
        // Python asserts message_range_kwargs(2, 5) == {"msg_start": 2, "msg_end": 5};
        // Java encodes ranges through firstChangedIndex, the shared helper both
        // range kwargs builders delegate to in Python.
        assertThat(KVCacheMetadata.firstChangedIndex(
                List.of("a", "b", "c"), List.of("a", "x", "c"))).hasValue(1);
        assertThat(KVCacheMetadata.firstChangedIndex(
                List.of("a", "b", "c", "d"), List.of("a", "b"))).hasValue(2);
        assertThat(KVCacheMetadata.firstChangedIndex(
                List.of("a"), List.of("a", "b"))).isEmpty();
    }

    @Test
    void sessionLineageIgnoresNonStringAffinityOverrides() {
        AgentSession session = new AgentSession("session-id", Map.of(), null);
        session.getEnvs().put(KVCacheMetadata.KV_CACHE_AFFINITY_SESSION_ID_ENV, new Object());

        KVCacheMetadata.Lineage lineage = KVCacheMetadata.resolveSessionLineage(session);

        assertThat(lineage.sessionId()).isEqualTo("session-id");
        assertThat(lineage.parentSessionId()).isEqualTo("session-id");
    }

    @Test
    void teamMemberCacheIdentityUsesCardIdScope() {
        assertThat(KVCacheMetadata.teamMemberCacheIdentity("team-sid", "team-a", "coder"))
                .isEqualTo("team:team-sid:team:team-a:member:coder");
    }

    @Test
    void roundLevelCompressorIdentityIsAStableChild() {
        KVCacheIdentity identity = KVCacheMetadata.contextCompressorCacheIdentity(
                "session-a", "RoundLevelCompressor");

        assertThat(identity.cacheId()).isEqualTo("session-a:compressor:round-level");
        assertThat(identity.parentCacheId()).isEqualTo("session-a");
    }

    @Test
    void currentRoundCompressorIdentityIsAStableChild() {
        KVCacheIdentity identity = KVCacheMetadata.contextCompressorCacheIdentity(
                "session-a", "CurrentRoundCompressor");

        assertThat(identity.cacheId()).isEqualTo("session-a:compressor:current-round");
        assertThat(identity.parentCacheId()).isEqualTo("session-a");
    }

    @Test
    void dialogueCompressorIdentityIsAStableChild() {
        KVCacheIdentity identity = KVCacheMetadata.contextCompressorCacheIdentity(
                "session-a", "DialogueCompressor");

        assertThat(identity.cacheId()).isEqualTo("session-a:compressor:dialogue");
        assertThat(identity.parentCacheId()).isEqualTo("session-a");
    }

    @Test
    void contextCompressorCacheIdentityRejectsUnknownType() {
        assertThatThrownBy(() -> KVCacheMetadata.contextCompressorCacheIdentity(
                "session-a", "UnknownCompressor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported context compressor");
    }
}
