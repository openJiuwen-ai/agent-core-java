/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Shared schema-version metadata columns for memory SQL models.
 *
 * <p>Mirrors Python's {@code MemoryMetaMixin} in
 * {@code openjiuwen/core/memory/manage/mem_model/db_model.py}.</p>
 */
public abstract class MemoryMetaMixin {

    private String tableName;
    private String schemaVersion;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
}
