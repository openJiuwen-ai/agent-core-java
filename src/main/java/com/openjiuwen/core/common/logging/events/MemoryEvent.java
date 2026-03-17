/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/** Memory operation related event. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MemoryEvent extends BaseLogEvent {
    private String memoryType;
    private String operation;
    private List<String> memoryId;
    private String query;
    private Integer memoryCount;
    private List<Map<String, Object>> retrievedMemories;
    private Integer storageSizeBytes;
    private String userId;
    private String scopeId;

    public MemoryEvent() {
        super();
        setModuleType(ModuleType.MEMORY);
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "memory_type", memoryType);
        putIfNotNull(map, "operation", operation);
        putIfNotNull(map, "memory_id", memoryId);
        putIfNotNull(map, "query", query);
        putIfNotNull(map, "memory_count", memoryCount);
        putIfNotNull(map, "retrieved_memories", retrievedMemories);
        putIfNotNull(map, "storage_size_bytes", storageSizeBytes);
        putIfNotNull(map, "user_id", userId);
        putIfNotNull(map, "scope_id", scopeId);
    }
}
