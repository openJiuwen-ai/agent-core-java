/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import java.util.Objects;

/**
 * Memory item paired with an add/delete action.
 *
 * <p>Mirrors Python's {@code MemoryActionItem} in
 * {@code openjiuwen/core/memory/manage/update/mem_update_checker.py}.</p>
 */
public record MemoryActionItem(String id, String content, MemoryStatus status) {

    public MemoryActionItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(status, "status");
    }
}
