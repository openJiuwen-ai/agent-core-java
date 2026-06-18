/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.store.BaseMessageStore;
import com.openjiuwen.core.foundation.store.MessageMetadata;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateMessageOperation;

import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>Mirrors Python's {@code MessageMigrator} in
 * {@code openjiuwen/core/memory/migration/migrator/message_migrator.py}.</p>
 */
public class MessageMigrator {

    public static final String MESSAGE_ENTITY_KEY = "message_global";
    private static final int BACKUP_PAGE_SIZE = 1000;

    private final BaseMessageStore messageStore;

    public MessageMigrator(BaseMessageStore messageStore) {
        this.messageStore = messageStore;
    }

    public CompletableFuture<Boolean> tryMigrate(String entityKey, List<BaseOperation> operations) {
        if (!MESSAGE_ENTITY_KEY.equals(entityKey)) {
            Loggers.MEMORY.error(
                    "Unsupported entity_key: '{}'. Expected: '{}'",
                    entityKey,
                    MESSAGE_ENTITY_KEY,
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

        return messageStore.getSchemaVersion().thenCompose(currentVersion -> {
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

            AtomicReference<List<BackupRecord>> backupRef = new AtomicReference<>();
            return createBackup()
                    .thenCompose(backupData -> {
                        backupRef.set(backupData);
                        return executeOperations(pendingOperations, currentVersion);
                    })
                    .thenCompose(lastVersion -> {
                        if (currentVersion != null && currentVersion == lastVersion) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return messageStore.setSchemaVersion(lastVersion).thenRun(() -> Loggers.MEMORY.info(
                                "Message schema version updated from {} to {}",
                                currentVersion,
                                lastVersion,
                                LogEventType.MEMORY_INIT
                        ));
                    })
                    .thenApply(ignored -> true)
                    .exceptionallyCompose(error -> {
                        Throwable cause = rootCause(error);
                        Loggers.MEMORY.error(
                                "Error during message migration: {}",
                                cause.getMessage(),
                                LogEventType.MEMORY_INIT,
                                cause
                        );
                        List<BackupRecord> backupData = backupRef.get();
                        if (backupData == null) {
                            return CompletableFuture.completedFuture(false);
                        }
                        return restoreFromBackup(backupData, currentVersion)
                                .handle((ignored, restoreError) -> Boolean.FALSE);
                    });
        });
    }

    private CompletableFuture<Void> executeOperation(BaseOperation operation) {
        if (operation instanceof UpdateMessageOperation updateMessageOperation) {
            return updateMessageOperation.getUpdateFunc().apply(messageStore);
        }
        return CompletableFuture.failedFuture(ErrorHelper.buildError(
                StatusCode.MEMORY_MIGRATE_MEMORY_EXECUTION_ERROR,
                "error_msg",
                "Unsupported operation type: " + operation.getClass().getSimpleName()
        ));
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

    private CompletableFuture<List<BackupRecord>> createBackup() {
        return messageStore.getMessages(new LinkedHashMap<>(), BACKUP_PAGE_SIZE, "timestamp", "asc")
                .thenApply(messages -> {
                    List<BackupRecord> backupData = new ArrayList<>();
                    if (messages != null) {
                        for (Map.Entry<BaseMessage, MessageMetadata> entry : messages) {
                            backupData.add(BackupRecord.from(entry));
                        }
                    }
                    Loggers.MEMORY.info(
                            "Created message backup with {} records",
                            backupData.size(),
                            LogEventType.MEMORY_INIT
                    );
                    return backupData;
                });
    }

    private CompletableFuture<Void> restoreFromBackup(List<BackupRecord> backupData, Integer preMigrationVersion) {
        return messageStore.deleteMessages(new LinkedHashMap<>())
                .thenCompose(ignored -> {
                    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                    for (BackupRecord record : backupData) {
                        chain = chain.thenCompose(previous -> messageStore.addMessage(record.toAddRequest())
                                .thenApply(messageId -> null));
                    }
                    return chain;
                })
                .thenRun(() -> Loggers.MEMORY.info(
                        "Successfully restored {} messages from backup",
                        backupData.size(),
                        LogEventType.MEMORY_INIT
                ))
                .exceptionally(error -> {
                    Loggers.MEMORY.error(
                            "Failed to restore messages from backup: {}",
                            rootCause(error).getMessage(),
                            LogEventType.MEMORY_INIT,
                            rootCause(error)
                    );
                    return null;
                })
                .thenCompose(ignored -> {
                    if (preMigrationVersion == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return messageStore.setSchemaVersion(preMigrationVersion)
                            .thenRun(() -> Loggers.MEMORY.info(
                                    "Reset schema version to pre-migration value: {}",
                                    preMigrationVersion,
                                    LogEventType.MEMORY_INIT
                            ))
                            .exceptionally(error -> {
                                Loggers.MEMORY.error(
                                        "Failed to reset schema version to {}: {}",
                                        preMigrationVersion,
                                        rootCause(error).getMessage(),
                                        LogEventType.MEMORY_INIT,
                                        rootCause(error)
                                );
                                return null;
                            });
                });
    }

    private static boolean validateOperationsOrder(List<BaseOperation> operations) {
        for (int index = 0; index < operations.size() - 1; index++) {
            if (operations.get(index).getSchemaVersion() >= operations.get(index + 1).getSchemaVersion()) {
                return false;
            }
        }
        return true;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        if (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * <p>Mirrors Python's serialized backup record shape in
     * {@code openjiuwen/core/memory/migration/migrator/message_migrator.py}.</p>
     */
    private record BackupRecord(
            Object content,
            String role,
            String userId,
            String scopeId,
            String sessionId,
            ZonedDateTime timestamp
    ) {
        private static BackupRecord from(Map.Entry<BaseMessage, MessageMetadata> entry) {
            BaseMessage message = entry == null ? null : entry.getKey();
            MessageMetadata metadata = entry == null ? null : entry.getValue();
            return new BackupRecord(
                    message == null ? "" : message.getContent(),
                    message == null ? "" : nullToEmpty(message.getRole()),
                    metadata == null ? "" : nullToEmpty(metadata.getUserId()),
                    metadata == null ? "" : nullToEmpty(metadata.getScopeId()),
                    metadata == null ? "" : nullToEmpty(metadata.getSessionId()),
                    metadata == null ? null : metadata.getTimestamp()
            );
        }

        private Map<String, Object> toAddRequest() {
            Map<String, Object> addRequest = new LinkedHashMap<>();
            addRequest.put("message", new BaseMessage(role, content));
            addRequest.put("user_id", userId);
            addRequest.put("scope_id", scopeId);
            addRequest.put("session_id", sessionId);
            addRequest.put("timestamp", timestamp);
            return addRequest;
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }
}
