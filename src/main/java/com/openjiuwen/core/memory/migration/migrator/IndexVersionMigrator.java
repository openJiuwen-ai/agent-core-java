/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.memory.migration.operation.AddMemoryDocFieldOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.RemoveMemoryDocFieldOperation;
import com.openjiuwen.core.memory.migration.operation.RenameMemoryDocFieldOperation;
import com.openjiuwen.core.memory.migration.operation.TransformMemoryDocFieldOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * <p>Mirrors Python's {@code IndexVersionMigrator} in
 * {@code openjiuwen/core/memory/migration/migrator/index_version_migrator.py}.</p>
 */
public class IndexVersionMigrator {

    private static final int BATCH_SIZE = 100;

    public CompletableFuture<Boolean> tryMigrate(BaseMemoryIndex index, List<BaseOperation> operations) {
        int currentVersion = index.getSchemaVersion();
        List<BaseOperation> operationsToApply = operations == null
                ? List.of()
                : operations.stream()
                        .filter(operation -> operation.getSchemaVersion() > currentVersion)
                        .toList();

        if (operationsToApply.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        AtomicReference<String> backupIdRef = new AtomicReference<>();
        return index.createBackup()
                .thenCompose(backupId -> {
                    backupIdRef.set(backupId);
                    return executeOperations(index, operationsToApply);
                })
                .thenCompose(ignored -> index.cleanupBackup(backupIdRef.get()))
                .thenApply(ignored -> true)
                .exceptionallyCompose(error -> {
                    Throwable cause = rootCause(error);
                    String backupId = backupIdRef.get();
                    CompletableFuture<Void> restoreFuture = backupId == null
                            ? CompletableFuture.completedFuture(null)
                            : index.restoreBackup(backupId)
                                    .thenCompose(ignored -> index.cleanupBackup(backupId));
                    return restoreFuture.handle((ignored, restoreError) -> {
                        index.updateSchemaVersion(currentVersion);
                        Loggers.MEMORY.error(
                                "Error during index migration: {}",
                                cause.getMessage(),
                                LogEventType.MEMORY_INIT,
                                cause
                        );
                        return false;
                    });
                });
    }

    private CompletableFuture<Void> executeOperations(BaseMemoryIndex index, List<BaseOperation> operationsToApply) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (BaseOperation operation : operationsToApply) {
            chain = chain.thenCompose(ignored -> applyOperation(index, operation)
                    .thenRun(() -> index.updateSchemaVersion(operation.getSchemaVersion())));
        }
        return chain;
    }

    private CompletableFuture<Void> applyOperation(BaseMemoryIndex index, BaseOperation operation) {
        if (operation instanceof RenameMemoryDocFieldOperation renameOperation) {
            return applyToDocuments(index, document -> applyRenameField(document, renameOperation));
        }
        if (operation instanceof TransformMemoryDocFieldOperation transformOperation) {
            return applyToDocuments(index, document -> applyTransformField(document, transformOperation));
        }
        if (operation instanceof AddMemoryDocFieldOperation addOperation) {
            return applyToDocuments(index, document -> applyAddField(document, addOperation));
        }
        if (operation instanceof RemoveMemoryDocFieldOperation removeOperation) {
            return applyToDocuments(index, document -> applyRemoveField(document, removeOperation));
        }
        return CompletableFuture.failedFuture(ErrorHelper.buildError(
                StatusCode.MEMORY_MIGRATE_MEMORY_EXECUTION_ERROR,
                "error_msg",
                "Unsupported operation type: " + operation.getClass().getSimpleName()
        ));
    }

    private CompletableFuture<Void> applyToDocuments(BaseMemoryIndex index, Consumer<MemoryDoc> documentConsumer) {
        return index.listUserScopes().thenCompose(scopes -> {
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (BaseMemoryIndex.UserScopeKey scope : scopes) {
                chain = chain.thenCompose(ignored -> processScope(
                        index,
                        scope.userId(),
                        scope.scopeId(),
                        0,
                        documentConsumer
                ));
            }
            return chain;
        });
    }

    private CompletableFuture<Void> processScope(
            BaseMemoryIndex index,
            String userId,
            String scopeId,
            int offset,
            Consumer<MemoryDoc> documentConsumer
    ) {
        return index.listMemories(userId, scopeId, offset, BATCH_SIZE, null)
                .thenCompose(documents -> {
                    if (documents == null || documents.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    for (MemoryDoc document : documents) {
                        documentConsumer.accept(document);
                    }

                    List<String> documentIds = new ArrayList<>();
                    for (MemoryDoc document : documents) {
                        documentIds.add(document.getId());
                    }

                    return index.deleteMemories(userId, scopeId, documentIds)
                            .thenCompose(ignored -> index.addMemories(userId, scopeId, documents))
                            .thenCompose(ignored -> processScope(
                                    index,
                                    userId,
                                    scopeId,
                                    offset + BATCH_SIZE,
                                    documentConsumer
                            ));
                });
    }

    private static void applyRenameField(MemoryDoc document, RenameMemoryDocFieldOperation operation) {
        Map<String, Object> fields = document.getFields();
        if (fields.containsKey(operation.getOldFieldName())) {
            Object value = fields.remove(operation.getOldFieldName());
            fields.put(operation.getNewFieldName(), value);
        }
    }

    private static void applyTransformField(MemoryDoc document, TransformMemoryDocFieldOperation operation) {
        Map<String, Object> fields = document.getFields();
        if (fields.containsKey(operation.getFieldName())) {
            fields.put(operation.getFieldName(), operation.getTransformFunc().apply(fields.get(operation.getFieldName())));
        }
    }

    private static void applyAddField(MemoryDoc document, AddMemoryDocFieldOperation operation) {
        Map<String, Object> fields = document.getFields();
        if (!fields.containsKey(operation.getFieldName())) {
            fields.put(operation.getFieldName(), operation.resolveDefaultValue());
        }
    }

    private static void applyRemoveField(MemoryDoc document, RemoveMemoryDocFieldOperation operation) {
        document.getFields().remove(operation.getFieldName());
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        if (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
