/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.memory.manage.mem_model.SupportMemoryType;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code VectorMigrator} in
 * {@code openjiuwen/core/memory/migration/migrator/vector_migrator.py}.
 */
public class VectorMigrator {

    private static final String VECTOR_PREFIX = "vector_";

    private final BaseVectorStore vectorStore;

    public VectorMigrator(BaseVectorStore vectorStore) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
    }

    public CompletableFuture<Boolean> tryMigrate(String entityKey, List<BaseOperation> operations) {
        return findCollections(entityKey)
                .thenCompose(collectionNames -> migrateCollections(collectionNames, operations))
                .thenApply(ignored -> Boolean.TRUE);
    }

    private CompletableFuture<Void> migrateCollections(List<String> collectionNames, List<BaseOperation> operations) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String collectionName : collectionNames) {
            chain = chain.thenCompose(ignored -> migrateCollection(collectionName, operations));
        }
        return chain;
    }

    private CompletableFuture<Void> migrateCollection(String collectionName, List<BaseOperation> operations) {
        return vectorStore.getCollectionMetadata(collectionName)
                .thenCompose(metadata -> {
                    int currentVersion = readSchemaVersion(metadata);
                    List<BaseOperation> operationsToApply = operations.stream()
                            .filter(operation -> operation.getSchemaVersion() > currentVersion)
                            .toList();
                    if (operationsToApply.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    int maxVersion = operationsToApply.stream()
                            .mapToInt(BaseOperation::getSchemaVersion)
                            .max()
                            .orElse(currentVersion);
                    return vectorStore.updateSchema(collectionName, operationsToApply)
                            .thenCompose(ignored -> vectorStore.updateCollectionMetadata(
                                    collectionName,
                                    Map.of("schema_version", maxVersion)
                            ));
                });
    }

    private CompletableFuture<List<String>> findCollections(String memTypeStr) {
        String normalized = normalizeMemoryType(memTypeStr);
        validateMemoryType(normalized);
        String suffix = "_" + normalized;
        return vectorStore.listCollectionNames()
                .thenApply(collectionNames -> collectionNames.stream()
                        .filter(collectionName -> collectionName.endsWith(suffix))
                        .toList());
    }

    private String normalizeMemoryType(String memTypeStr) {
        if (memTypeStr != null && memTypeStr.startsWith(VECTOR_PREFIX)) {
            return memTypeStr.substring(VECTOR_PREFIX.length());
        }
        return memTypeStr;
    }

    private void validateMemoryType(String memTypeStr) {
        List<String> supportedTypes = Arrays.stream(SupportMemoryType.values())
                .map(SupportMemoryType::getValue)
                .sorted()
                .toList();
        if (!supportedTypes.contains(memTypeStr)) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_MIGRATE_MEMORY_EXECUTION_ERROR,
                    "error_msg",
                    "Unsupported memory type: '" + memTypeStr + "'. Supported types: " + supportedTypes
            );
        }
    }

    private int readSchemaVersion(Map<String, Object> metadata) {
        Object schemaVersion = metadata == null ? 0 : metadata.getOrDefault("schema_version", 0);
        if (schemaVersion instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
