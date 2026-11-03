/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for memory index implementations.
 *
 * <p>Mirrors Python's {@code BaseMemoryIndex} in
 * {@code openjiuwen/core/foundation/store/base_memory_index.py}.</p>
 */
public abstract class BaseMemoryIndex {

    public abstract void setStorageCodec(StorageCodec codec);

    public abstract CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories);

    public abstract CompletableFuture<Void> updateMemories(String userId, String scopeId, List<MemoryDoc> memories);

    public abstract CompletableFuture<Void> deleteMemories(String userId, String scopeId, List<String> ids);

    public abstract CompletableFuture<Void> deleteByUser(String userId);

    public abstract CompletableFuture<Void> deleteByScope(String scopeId);

    public abstract CompletableFuture<Void> deleteByUserAndScope(String userId, String scopeId);

    public abstract CompletableFuture<List<MemorySearchResult>> search(
            String userId,
            String scopeId,
            String query,
            List<String> memTypes,
            int topK
    );

    public abstract CompletableFuture<MemoryDoc> getById(String userId, String scopeId, String memId);

    public CompletableFuture<List<MemoryDoc>> listMemories(
            String userId,
            String scopeId,
            int offset,
            int limit,
            List<String> memTypes
    ) {
        return CompletableFuture.completedFuture(List.of());
    }

    public int getSchemaVersion() {
        return 0;
    }

    public void updateSchemaVersion(int version) {
    }

    public CompletableFuture<String> createBackup() {
        return CompletableFuture.completedFuture("");
    }

    public CompletableFuture<Void> restoreBackup(String backupId) {
        return CompletableFuture.completedFuture(null);
    }

    public abstract CompletableFuture<Void> cleanupBackup(String backupId);

    public abstract CompletableFuture<List<UserScopeKey>> listUserScopes();

    public record MemorySearchResult(MemoryDoc document, double score) {
    }

    public record UserScopeKey(String userId, String scopeId) {
    }
}
