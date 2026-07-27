/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import java.util.List;

/**
 * Abstract persistence surface for sandbox lifecycle records.
 */
public interface AbstractSandboxStore {
    SandboxRecord get(String key);

    void set(String key, SandboxRecord record);

    SandboxRecord hdel(String key);

    List<SandboxRecord> flushdb();

    List<SandboxRecord> evictExpired(int idleTtlSeconds, double now);
}
