/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.memory.migration.migrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.spi.store.BaseKVStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * KV data migrator with backup and rollback support.
 */
public class KvMigrator {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String KV_SCHEMA_VERSION = "MEMORY_MIGRATION_KV_SCHEMA_VERSION";
    public static final String KV_ENTITY_KEY = "kv_global";

    private final BaseKVStore kvStore;

    public KvMigrator(BaseKVStore kvStore) {
        this.kvStore = kvStore;
    }

    public boolean tryMigrate(String entityKey, List<BaseOperation> operations) {
        if (!KV_ENTITY_KEY.equals(entityKey)) {
            MEMORY_LOGGER.error("[{}] Unsupported entity_key: '{}'. Expected: '{}'",
                    LogEventType.MEMORY_INIT, entityKey, KV_ENTITY_KEY);
            return false;
        }
        if (operations == null || operations.isEmpty()) {
            return true;
        }
        if (!validateOperationsOrder(operations)) {
            MEMORY_LOGGER.error("[{}] Operations are not in ascending order by schema_version",
                    LogEventType.MEMORY_INIT);
            return false;
        }

        Integer currentVersion = getCurrentVersion();
        int lastOperationVersion = operations.get(operations.size() - 1).getSchemaVersion();
        if (currentVersion != null && currentVersion >= lastOperationVersion) {
            MEMORY_LOGGER.info("[{}] KV version {} >= {}, no migration needed",
                    LogEventType.MEMORY_INIT, currentVersion, lastOperationVersion);
            return true;
        }

        final Integer cv = currentVersion;
        List<BaseOperation> pendingOps = operations.stream()
                .filter(op -> cv == null || op.getSchemaVersion() > cv)
                .toList();
        if (pendingOps.isEmpty()) {
            return true;
        }

        MEMORY_LOGGER.info("[{}] Found {} pending KV operations", LogEventType.MEMORY_INIT, pendingOps.size());
        String backupKey = null;
        try {
            backupKey = createBackup(currentVersion);

            int lastVersion = currentVersion != null ? currentVersion : 0;
            for (int i = 0; i < pendingOps.size(); i++) {
                BaseOperation op = pendingOps.get(i);
                MEMORY_LOGGER.info("[{}] Executing KV operation {}/{}: {} (v={})",
                        LogEventType.MEMORY_INIT, i + 1, pendingOps.size(),
                        op.getClass().getSimpleName(), op.getSchemaVersion());
                executeOperation(op);
                lastVersion = op.getSchemaVersion();
            }

            if (currentVersion == null || lastVersion != currentVersion) {
                updateVersion(lastVersion);
            }
            cleanupBackup(backupKey);
            return true;
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] KV migration error: {}", LogEventType.MEMORY_INIT, e.getMessage());
            if (backupKey != null) {
                restoreFromBackup(backupKey);
            }
            return false;
        }
    }

    private Integer getCurrentVersion() {
        Object vVal = kvStore.get(KV_SCHEMA_VERSION);
        if (vVal == null) {
            boolean hasData = hasMemoryModuleData();
            if (!hasData) {
                int initialVersion = MigrationPlan.getKvRegistry().getCurrentVersion(KV_ENTITY_KEY);
                updateVersion(initialVersion);
                return initialVersion;
            }
            return null;
        }
        if (vVal instanceof Integer) return (Integer) vVal;
        if (vVal instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid KV_SCHEMA_VERSION format: " + s);
            }
        }
        throw new IllegalStateException("Invalid KV_SCHEMA_VERSION type: " + vVal.getClass().getName());
    }

    private boolean hasMemoryModuleData() {
        Set<String> prefixes = KvPrefixRegistry.getInstance().getAllPrefixes();
        for (String prefix : prefixes) {
            Map<String, Object> data = kvStore.getByPrefix(prefix);
            if (data != null && !data.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void updateVersion(int version) {
        kvStore.set(KV_SCHEMA_VERSION, String.valueOf(version));
    }

    @SuppressWarnings("unchecked")
    private void executeOperation(BaseOperation op) {
        String className = op.getClass().getSimpleName();
        if ("UpdateKVOperation".equals(className)) {
            try {
                Consumer<BaseKVStore> func = (Consumer<BaseKVStore>)
                        op.getClass().getMethod("getUpdateFunc").invoke(op);
                func.accept(kvStore);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to execute KV operation", e);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported KV operation: " + className);
        }
    }

    private static boolean validateOperationsOrder(List<BaseOperation> operations) {
        for (int i = 0; i < operations.size() - 1; i++) {
            if (operations.get(i).getSchemaVersion() >= operations.get(i + 1).getSchemaVersion()) {
                return false;
            }
        }
        return true;
    }

    private String createBackup(Integer currentVersion) {
        String backupKey = KV_SCHEMA_VERSION + "_BACKUP_" + System.currentTimeMillis();
        Map<String, Object> backupData = new LinkedHashMap<>();
        Set<String> prefixes = KvPrefixRegistry.getInstance().getAllPrefixes();
        for (String prefix : prefixes) {
            Map<String, Object> data = kvStore.getByPrefix(prefix);
            if (data != null) {
                backupData.putAll(data);
            }
        }
        if (currentVersion != null) {
            Object versionValue = kvStore.get(KV_SCHEMA_VERSION);
            if (versionValue != null) {
                backupData.put(KV_SCHEMA_VERSION, versionValue);
            }
        }
        if (!backupData.isEmpty()) {
            try {
                kvStore.set(backupKey, MAPPER.writeValueAsString(backupData));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize backup", e);
            }
        }
        return backupKey;
    }

    private void restoreFromBackup(String backupKey) {
        Object backupJson = kvStore.get(backupKey);
        if (backupJson == null) {
            MEMORY_LOGGER.error("[{}] Backup not found: {}", LogEventType.MEMORY_INIT, backupKey);
            return;
        }
        try {
            Map<String, Object> backupData = MAPPER.readValue(String.valueOf(backupJson),
                    new TypeReference<>() {});
            Set<String> prefixes = KvPrefixRegistry.getInstance().getAllPrefixes();
            for (String prefix : prefixes) {
                kvStore.deleteByPrefix(prefix, null);
            }
            for (Map.Entry<String, Object> e : backupData.entrySet()) {
                kvStore.set(e.getKey(), e.getValue());
            }
            MEMORY_LOGGER.info("[{}] Restored {} keys from backup", LogEventType.MEMORY_INIT, backupData.size());
        } catch (JsonProcessingException e) {
            MEMORY_LOGGER.error("[{}] Failed to decode backup data: {}", LogEventType.MEMORY_INIT, e.getMessage());
        }
    }

    private void cleanupBackup(String backupKey) {
        try {
            kvStore.delete(backupKey);
        } catch (Exception e) {
            MEMORY_LOGGER.warn("[{}] Failed to cleanup backup {}: {}",
                    LogEventType.MEMORY_INIT, backupKey, e.getMessage());
        }
    }
}
