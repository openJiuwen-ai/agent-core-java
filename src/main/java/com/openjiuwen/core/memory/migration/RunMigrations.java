/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.BaseMessageStore;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.migration.migrator.IndexVersionMigrator;
import com.openjiuwen.core.memory.migration.migrator.KvMigrator;
import com.openjiuwen.core.memory.migration.migrator.MessageMigrator;
import com.openjiuwen.core.memory.migration.migrator.SqlMigrator;
import com.openjiuwen.core.memory.migration.migrator.VectorMigrator;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;

/**
 * <p>Mirrors Python's module {@code openjiuwen.core.memory.migration.run_migrations} in
 * {@code openjiuwen/core/memory/migration/run_migrations.py}.</p>
 */
public final class RunMigrations {

    private RunMigrations() {
    }

    public static CompletableFuture<Void> runVectorMigrations(BaseVectorStore vectorStore) {
        return runMigrationsWithRegistry(
                MigrationPlan.getVectorRegistry(),
                new VectorMigrator(vectorStore)::tryMigrate,
                "vector store"
        );
    }

    public static CompletableFuture<Void> runKvMigrations(BaseKVStore kvStore) {
        return runMigrationsWithRegistry(
                MigrationPlan.getKvRegistry(),
                new KvMigrator(kvStore)::tryMigrate,
                "kv store"
        );
    }

    public static CompletableFuture<Void> runSqlMigrations(SqlDbStore sqlDbStore) {
        return runMigrationsWithRegistry(
                MigrationPlan.getSqlRegistry(),
                new SqlMigrator(sqlDbStore)::tryMigrate,
                "db store"
        );
    }

    public static CompletableFuture<Void> runMessageMigrations(BaseMessageStore messageStore) {
        return runMigrationsWithRegistry(
                MigrationPlan.getMessageRegistry(),
                new MessageMigrator(messageStore)::tryMigrate,
                "message"
        );
    }

    public static CompletableFuture<Void> runIndexVersionMigrations(BaseMemoryIndex index) {
        return runMigrationsWithRegistry(
                MigrationPlan.getIndexRegistry(),
                (entityKey, operations) -> new IndexVersionMigrator().tryMigrate(index, operations),
                "index version"
        );
    }

    private static CompletableFuture<Void> runMigrationsWithRegistry(
            OperationRegistry registry,
            BiFunction<String, List<BaseOperation>, CompletableFuture<Boolean>> migrator,
            String storeName
    ) {
        Map<String, List<BaseOperation>> registryMap = registry.getAllOperations();
        if (registryMap.isEmpty()) {
            Loggers.MEMORY.info(
                    "No {} migrations registered, skipping migration process",
                    storeName,
                    LogEventType.MEMORY_INIT
            );
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Map.Entry<String, List<BaseOperation>> entry : registryMap.entrySet()) {
            String entityKey = entry.getKey();
            List<BaseOperation> operations = entry.getValue();
            chain = chain.thenCompose(ignored -> migrator.apply(entityKey, operations)
                    .thenCompose(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return CompletableFuture.<Void>failedFuture(migrationError(
                                storeName,
                                entityKey,
                                null
                        ));
                    })
                    .exceptionallyCompose(error -> {
                        Throwable cause = rootCause(error);
                        Loggers.MEMORY.error(
                                "Error during {} migration for entity {}: {}",
                                storeName,
                                entityKey,
                                cause.getMessage(),
                                LogEventType.MEMORY_INIT,
                                cause
                        );
                        return CompletableFuture.<Void>failedFuture(migrationError(storeName, entityKey, cause));
                    }));
        }
        return chain;
    }

    private static RuntimeException migrationError(String storeName, String entityKey, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.MEMORY_MIGRATE_MEMORY_EXECUTION_ERROR,
                null,
                null,
                cause,
                Map.of("error_msg", storeName + " migrations failed for entity: " + entityKey)
        );
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        if (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
