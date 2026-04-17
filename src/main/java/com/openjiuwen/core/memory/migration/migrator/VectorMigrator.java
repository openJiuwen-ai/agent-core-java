/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.memory.manage.mem_model.SemanticStore;
import com.openjiuwen.core.memory.manage.mem_model.SupportMemoryType;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vector store migrator.
 */
public class VectorMigrator {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;

    private final SemanticStore semanticStore;

    public VectorMigrator(SemanticStore semanticStore) {
        this.semanticStore = semanticStore;
    }

    public boolean tryMigrate(String entityKey, List<BaseOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return true;
        }
        try {
            List<String> collectionNames = findCollections(entityKey);
            for (String collectionName : collectionNames) {
                Map<String, Object> metadata = semanticStore.getCollectionMetadata(collectionName);
                int currentVersion = metadata.get("schema_version") instanceof Number number ? number.intValue() : 0;

                List<BaseOperation> operationsToApply = new ArrayList<>();
                for (BaseOperation operation : operations) {
                    if (operation.getSchemaVersion() > currentVersion) {
                        operationsToApply.add(operation);
                    }
                }
                if (operationsToApply.isEmpty()) {
                    continue;
                }

                boolean updated = semanticStore.updateSchema(collectionName, operationsToApply);
                if (!updated) {
                    MEMORY_LOGGER.error("[{}] Vector schema operations are not supported by current store, collection={}",
                            LogEventType.MEMORY_INIT, collectionName);
                    return false;
                }

                int maxVersion = operationsToApply.stream()
                        .mapToInt(BaseOperation::getSchemaVersion)
                        .max()
                        .orElse(currentVersion);
                semanticStore.updateCollectionMetadata(collectionName, Map.of("schema_version", maxVersion));
                MEMORY_LOGGER.info("[{}] Applied {} vector migration operations for collection {} -> schema_version={}",
                        LogEventType.MEMORY_INIT, operationsToApply.size(), collectionName, maxVersion);
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Vector migration failed for entity {}: {}",
                    LogEventType.MEMORY_INIT, entityKey, e.getMessage());
            return false;
        }
        return true;
    }

    private List<String> findCollections(String memType) {
        String normalized = memType != null && memType.startsWith("vector_")
                ? memType.substring("vector_".length())
                : memType;
        validateMemoryType(normalized);

        List<String> allCollections = semanticStore.listCollectionNames();
        String suffix = "_" + normalized;
        List<String> matched = new ArrayList<>();
        for (String collectionName : allCollections) {
            if (collectionName != null && collectionName.endsWith(suffix)) {
                matched.add(collectionName);
            }
        }
        return matched;
    }

    private void validateMemoryType(String memType) {
        Set<String> supportedTypes = new LinkedHashSet<>();
        for (SupportMemoryType type : SupportMemoryType.values()) {
            supportedTypes.add(type.getValue());
        }
        if (!supportedTypes.contains(memType)) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_MIGRATE_MEMORY_EXECUTION_ERROR,
                    "error_msg", "Unsupported memory type: '" + memType + "'. Supported types: " + supportedTypes
            );
        }
    }
}
