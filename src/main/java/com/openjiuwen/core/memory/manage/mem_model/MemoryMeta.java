/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Public row model matching the memory_meta table.
 */
public record MemoryMeta(String tableName, String schemaVersion) {
}