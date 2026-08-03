/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox.gateway;

import java.util.List;
import java.util.Optional;

/**
 * Mirrors Python's {@code AbstractSandboxStore} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/sandbox_store.py}.
 */
public abstract class AbstractSandboxStore {

    public abstract Optional<SandboxRecord> get(String key);

    public abstract void set(String key, SandboxRecord record);

    public abstract Optional<SandboxRecord> hdel(String key);

    public abstract List<SandboxRecord> flushdb();

    public abstract List<SandboxRecord> evictExpired(int idleTtlSeconds, double now);
}
