/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class BaseMemoryIndexTest {

    @Test
    void memoryDocRoundTripsThroughMap() {
        ZonedDateTime timestamp = ZonedDateTime.parse("2025-01-01T00:00:00Z");
        MemoryDoc memoryDoc = new MemoryDoc(
                "test_id_123",
                "Test memory content",
                "fragment",
                timestamp,
                Map.of("session_id", "session_123")
        );

        Map<String, Object> dumped = memoryDoc.toMap();
        MemoryDoc restored = MemoryDoc.fromMap(new LinkedHashMap<>(dumped));

        assertThat(dumped.get("id")).isEqualTo("test_id_123");
        assertThat(dumped.get("fields")).isEqualTo(Map.of("session_id", "session_123"));
        assertThat(restored).isEqualTo(memoryDoc);
    }

    @Test
    void storageCodecShapeCheckMatchesEncodeDecodeSurface() {
        assertThat(StorageCodec.isCodec(new Object())).isFalse();
        assertThat(StorageCodec.isCodec(new Object() {
            public String encode(String text) {
                return text;
            }

            public String decode(String data) {
                return data;
            }
        })).isTrue();
    }

    @Test
    void baseMemoryIndexDefaultsMirrorPythonHooks() {
        BaseMemoryIndex index = new BaseMemoryIndex() {
            @Override
            public void setStorageCodec(StorageCodec codec) {
            }

            @Override
            public CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> updateMemories(String userId, String scopeId, List<MemoryDoc> memories) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteMemories(String userId, String scopeId, List<String> ids) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteByUser(String userId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteByScope(String scopeId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteByUserAndScope(String userId, String scopeId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<List<MemorySearchResult>> search(
                    String userId,
                    String scopeId,
                    String query,
                    List<String> memTypes,
                    int topK
            ) {
                return CompletableFuture.completedFuture(List.of(new MemorySearchResult(new MemoryDoc(), 0.95)));
            }

            @Override
            public CompletableFuture<MemoryDoc> getById(String userId, String scopeId, String memId) {
                return CompletableFuture.completedFuture(new MemoryDoc());
            }

            @Override
            public CompletableFuture<Void> cleanupBackup(String backupId) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<List<UserScopeKey>> listUserScopes() {
                return CompletableFuture.completedFuture(List.of(new UserScopeKey("u", "s")));
            }
        };

        assertThat(index.listMemories("u", "s", 0, 100, null).join()).isEmpty();
        assertThat(index.getSchemaVersion()).isZero();
        assertThat(index.createBackup().join()).isEmpty();
        assertThat(index.search("u", "s", "q", null, 10).join()).hasSize(1);
        assertThat(index.listUserScopes().join()).containsExactly(new BaseMemoryIndex.UserScopeKey("u", "s"));
    }
}
