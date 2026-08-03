/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Memory SQL metadata row that records schema versions per table.
 *
 * <p>Mirrors Python's {@code MemoryMeta} in
 * {@code openjiuwen/core/memory/manage/mem_model/db_model.py}.</p>
 */
public class MemoryMeta extends MemoryMetaMixin {

    public static final String TABLE_NAME = "memory_meta";
}
