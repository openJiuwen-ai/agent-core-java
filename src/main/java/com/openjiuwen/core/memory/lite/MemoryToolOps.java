/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Memory tool operations interface.
 * <p>
 * Mirrors Python's MemoryToolOps.
 */
public interface MemoryToolOps {

    CompletableFuture<String> write(Map<String, Object> params);

    CompletableFuture<List<Map<String, Object>>> read(Map<String, Object> params);

    CompletableFuture<Boolean> delete(Map<String, Object> params);

    CompletableFuture<List<Map<String, Object>>> search(Map<String, Object> params);

    CompletableFuture<Map<String, Object>> update(Map<String, Object> params);
}