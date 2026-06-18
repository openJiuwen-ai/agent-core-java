/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>Mirrors Python's {@code KVMigrator} in
 * {@code openjiuwen/core/memory/migration/migrator/kv_migrator.py}.</p>
 */
public class KvMigrator {

    public static final String KV_SCHEMA_VERSION = "MEMORY_MIGRATION_KV_SCHEMA_VERSION";
    public static final String KV_ENTITY_KEY = "kv_global";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final BaseKVStore kvStore;

    public KvMigrator(BaseKVStore kvStore) {
        this.kvStore = kvStore;
    }

    public CompletableFuture<Boolean> tryMigrate(String entityKey, List<BaseOperation> operations) {
        if (!KV_ENTITY_KEY.equals(entityKey)) {
            Loggers.MEMORY.error(
                    "Unsupported entity_key: '{}'. Expected: '{}'",
                    entityKey,
                    KV_ENTITY_KEY,
                    LogEventType.MEMORY_INIT
            );
            return CompletableFuture.completedFuture(false);
        }
        if (operations == null || operations.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        if (!validateOperationsOrder(operations)) {
            Loggers.MEMORY.error(
                    "Operations are not in ascending order by schema_version",
                    LogEventType.MEMORY_INIT
            );
            return CompletableFuture.completedFuture(false);
        }

        return getCurrentVersion().thenCompose(currentVersion -> {
            int lastOperationVersion = operations.get(operations.size() - 1).getSchemaVersion();
            if (currentVersion != null && currentVersion >= lastOperationVersion) {
                Loggers.MEMORY.info(
                        "Current version {} is already >= last operation version {}, no migration needed",
                        currentVersion,
                        lastOperationVersion,
                        LogEventType.MEMORY_INIT
                );
                return CompletableFuture.completedFuture(true);
            }

            List<BaseOperation> pendingOperations = operations.stream()
                    .filter(operation -> currentVersion == null || operation.getSchemaVersion() > currentVersion)
                    .toList();
            if (pendingOperations.isEmpty()) {
                return CompletableFuture.completedFuture(true);
            }

            Loggers.MEMORY.info(
                    "Found {} pending operations to execute",
                    pendingOperations.size(),
                    LogEventType.MEMORY_INIT
            );

            AtomicReference<String> backupKeyRef = new AtomicReference<>();
            return createBackup(currentVersion)
                    .thenCompose(backupKey -> {
                        backupKeyRef.set(backupKey);
                        return executeOperations(pendingOperations, currentVersion);
                    })
                    .thenCompose(lastVersion -> {
                        CompletableFuture<Void> versionFuture = lastVersionEquals(currentVersion, lastVersion)
                                ? CompletableFuture.completedFuture(null)
                                : updateVersion(lastVersion).thenRun(() -> Loggers.MEMORY.info(
                                        "KV schema version updated from {} to {}",
                                        currentVersion,
                                        lastVersion,
                                        LogEventType.MEMORY_INIT
                                ));
                        return versionFuture.thenCompose(ignored -> cleanupBackup(backupKeyRef.get()));
                    })
                    .thenApply(ignored -> true)
                    .exceptionallyCompose(error -> {
                        Throwable cause = rootCause(error);
                        Loggers.MEMORY.error(
                                "Error during KV migration: {}",
                                cause.getMessage(),
                                LogEventType.MEMORY_INIT,
                                cause
                        );
                        String backupKey = backupKeyRef.get();
                        if (backupKey == null) {
                            return CompletableFuture.completedFuture(false);
                        }
                        return restoreFromBackup(backupKey)
                                .handle((ignored, restoreError) -> Boolean.FALSE);
                    });
        });
    }

    private CompletableFuture<Integer> getCurrentVersion() {
        return kvStore.get(KV_SCHEMA_VERSION).thenCompose(versionValue -> {
            if (versionValue == null) {
                return hasMemoryModuleData().thenCompose(hasMemoryData -> {
                    if (Boolean.TRUE.equals(hasMemoryData)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    int initialVersion = MigrationPlan.getKvRegistry().getCurrentVersion(KV_ENTITY_KEY);
                    return updateVersion(initialVersion).thenApply(ignored -> initialVersion);
                });
            }

            if (versionValue instanceof Number number) {
                return CompletableFuture.completedFuture(number.intValue());
            }
            if (versionValue instanceof String text) {
                if (text.chars().allMatch(Character::isDigit)) {
                    return CompletableFuture.completedFuture(Integer.parseInt(text));
                }
                String errorMsg = "Invalid SCHEMA_VERSION format: '" + text
                        + "'. Expected numeric string or integer.";
                Loggers.MEMORY.error(errorMsg, LogEventType.MEMORY_INIT);
                return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
            }

            String errorMsg = "Invalid SCHEMA_VERSION type: "
                    + versionValue.getClass().getSimpleName()
                    + ". Expected string or integer.";
            Loggers.MEMORY.error(errorMsg, LogEventType.MEMORY_INIT);
            return CompletableFuture.failedFuture(new IllegalArgumentException(errorMsg));
        });
    }

    private CompletableFuture<Boolean> hasMemoryModuleData() {
        Set<String> prefixes = KvPrefixRegistry.getInstance().getAllPrefixes();
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(false);
        for (String prefix : prefixes) {
            chain = chain.thenCompose(found -> {
                if (Boolean.TRUE.equals(found)) {
                    return CompletableFuture.completedFuture(true);
                }
                return kvStore.getByPrefix(prefix)
                        .thenApply(data -> data != null && !data.isEmpty());
            });
        }
        return chain;
    }

    private CompletableFuture<Void> updateVersion(int version) {
        return kvStore.set(KV_SCHEMA_VERSION, String.valueOf(version));
    }

    private CompletableFuture<Void> executeOperation(BaseOperation operation) {
        if (operation instanceof UpdateKVOperation updateKVOperation) {
            return updateKVOperation.getUpdateFunc().apply(kvStore);
        }
        return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unsupported operation type: " + operation.getClass().getSimpleName())
        );
    }

    private CompletableFuture<Integer> executeOperations(List<BaseOperation> pendingOperations, Integer currentVersion) {
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(
                currentVersion == null ? 0 : currentVersion
        );
        for (int index = 0; index < pendingOperations.size(); index++) {
            BaseOperation operation = pendingOperations.get(index);
            int operationIndex = index + 1;
            chain = chain.thenCompose(lastVersion -> {
                Loggers.MEMORY.info(
                        "Executing operation {}/{}: {} (schema_version={})",
                        operationIndex,
                        pendingOperations.size(),
                        operation.getClass().getSimpleName(),
                        operation.getSchemaVersion(),
                        LogEventType.MEMORY_INIT
                );
                return executeOperation(operation).thenApply(ignored -> {
                    Loggers.MEMORY.info(
                            "Successfully executed operation {} with schema_version {}",
                            operation.getClass().getSimpleName(),
                            operation.getSchemaVersion(),
                            LogEventType.MEMORY_INIT
                    );
                    return operation.getSchemaVersion();
                });
            });
        }
        return chain;
    }

    private static boolean validateOperationsOrder(List<BaseOperation> operations) {
        for (int index = 0; index < operations.size() - 1; index++) {
            if (operations.get(index).getSchemaVersion() >= operations.get(index + 1).getSchemaVersion()) {
                return false;
            }
        }
        return true;
    }

    private CompletableFuture<String> createBackup(Integer currentVersion) {
        String backupKey = KV_SCHEMA_VERSION + "_BACKUP_" + System.currentTimeMillis();
        Map<String, Object> backupData = new LinkedHashMap<>();

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String prefix : KvPrefixRegistry.getInstance().getAllPrefixes()) {
            chain = chain.thenCompose(ignored -> kvStore.getByPrefix(prefix)
                    .thenAccept(data -> {
                        if (data != null && !data.isEmpty()) {
                            backupData.putAll(data);
                        }
                    }));
        }

        if (currentVersion != null) {
            chain = chain.thenCompose(ignored -> kvStore.get(KV_SCHEMA_VERSION)
                    .thenAccept(versionValue -> {
                        if (versionValue != null) {
                            backupData.put(KV_SCHEMA_VERSION, versionValue);
                        }
                    }));
        }

        return chain.thenCompose(ignored -> {
            if (backupData.isEmpty()) {
                Loggers.MEMORY.info("Created backup with key: {}", backupKey, LogEventType.MEMORY_INIT);
                return CompletableFuture.completedFuture(backupKey);
            }
            try {
                String backupJson = OBJECT_MAPPER.writeValueAsString(backupData);
                return kvStore.set(backupKey, backupJson).thenApply(setIgnored -> {
                    Loggers.MEMORY.info("Created backup with key: {}", backupKey, LogEventType.MEMORY_INIT);
                    return backupKey;
                });
            } catch (JsonProcessingException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        });
    }

    private CompletableFuture<Void> restoreFromBackup(String backupKey) {
        return kvStore.get(backupKey).thenCompose(backupJson -> {
            if (backupJson == null) {
                Loggers.MEMORY.error("Backup not found: {}", backupKey, LogEventType.MEMORY_INIT);
                return CompletableFuture.completedFuture(null);
            }
            Map<String, Object> backupData;
            try {
                backupData = OBJECT_MAPPER.readValue(
                        String.valueOf(backupJson),
                        new TypeReference<LinkedHashMap<String, Object>>() {
                        }
                );
            } catch (JsonProcessingException exception) {
                Loggers.MEMORY.error(
                        "Failed to decode backup data: {}",
                        exception.getMessage(),
                        LogEventType.MEMORY_INIT
                );
                return CompletableFuture.failedFuture(exception);
            }

            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (String prefix : KvPrefixRegistry.getInstance().getAllPrefixes()) {
                chain = chain.thenCompose(ignored -> kvStore.deleteByPrefix(prefix, null));
            }
            for (Map.Entry<String, Object> entry : backupData.entrySet()) {
                chain = chain.thenCompose(ignored -> kvStore.set(entry.getKey(), entry.getValue()));
            }
            return chain.thenRun(() -> Loggers.MEMORY.info(
                    "Successfully restored {} keys from backup",
                    backupData.size(),
                    LogEventType.MEMORY_INIT
            ));
        });
    }

    private CompletableFuture<Void> cleanupBackup(String backupKey) {
        if (backupKey == null) {
            return CompletableFuture.completedFuture(null);
        }
        return kvStore.delete(backupKey)
                .thenRun(() -> Loggers.MEMORY.info("Cleaned up backup: {}", backupKey, LogEventType.MEMORY_INIT))
                .exceptionally(error -> {
                    Loggers.MEMORY.warning(
                            "Failed to cleanup backup {}: {}",
                            backupKey,
                            rootCause(error).getMessage(),
                            LogEventType.MEMORY_INIT
                    );
                    return null;
                });
    }

    private static boolean lastVersionEquals(Integer currentVersion, int lastVersion) {
        return currentVersion != null && currentVersion == lastVersion;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        if (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
