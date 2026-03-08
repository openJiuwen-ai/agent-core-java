/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.memory.manage.mem_model.SemanticStore;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.migration.migrator.KvMigrator;
import com.openjiuwen.core.memory.migration.migrator.SqlMigrator;
import com.openjiuwen.core.memory.migration.migrator.VectorMigrator;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationRegistry;
import com.openjiuwen.spi.store.BaseKVStore;
import java.util.List;

/**
 * Entry point for running all memory migrations (SQL, Vector, KV).
 */
public final class RunMigrations {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    private RunMigrations() {
    }

    public static boolean runSqlMigrations(SqlDbStore sqlDbStore) {
        OperationRegistry registry = MigrationPlan.getSqlRegistry();
        List<String> entityKeys = registry.getAllEntities();
        if (entityKeys.isEmpty()) {
            return true;
        }
        SqlMigrator migrator = new SqlMigrator(sqlDbStore);
        boolean allOk = true;
        for (String entityKey : entityKeys) {
            List<BaseOperation> ops = registry.getOperations(entityKey);
            if (!migrator.tryMigrate(entityKey, ops)) {
                MEMORY_LOGGER.error("[{}] SQL migration failed for entity: {}",
                        LogEventType.MEMORY_INIT, entityKey);
                allOk = false;
            }
        }
        return allOk;
    }

    public static boolean runVectorMigrations(SemanticStore semanticStore) {
        OperationRegistry registry = MigrationPlan.getVectorRegistry();
        List<String> entityKeys = registry.getAllEntities();
        if (entityKeys.isEmpty()) {
            return true;
        }
        VectorMigrator migrator = new VectorMigrator(semanticStore);
        boolean allOk = true;
        for (String entityKey : entityKeys) {
            List<BaseOperation> ops = registry.getOperations(entityKey);
            if (!migrator.tryMigrate(entityKey, ops)) {
                MEMORY_LOGGER.error("[{}] Vector migration failed for entity: {}",
                        LogEventType.MEMORY_INIT, entityKey);
                allOk = false;
            }
        }
        return allOk;
    }

    public static boolean runKvMigrations(BaseKVStore kvStore) {
        OperationRegistry registry = MigrationPlan.getKvRegistry();
        List<String> entityKeys = registry.getAllEntities();
        if (entityKeys.isEmpty()) {
            return true;
        }
        KvMigrator migrator = new KvMigrator(kvStore);
        boolean allOk = true;
        for (String entityKey : entityKeys) {
            List<BaseOperation> ops = registry.getOperations(entityKey);
            if (!migrator.tryMigrate(entityKey, ops)) {
                MEMORY_LOGGER.error("[{}] KV migration failed for entity: {}",
                        LogEventType.MEMORY_INIT, entityKey);
                allOk = false;
            }
        }
        return allOk;
    }
}
