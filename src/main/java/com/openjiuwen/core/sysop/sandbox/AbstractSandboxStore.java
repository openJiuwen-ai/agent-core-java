/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import java.util.List;

/**
 * Abstract persistence surface for sandbox lifecycle records.
 * 
 * @since 0.1.7
 */
public interface AbstractSandboxStore {
    /**
     * get.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    SandboxRecord get(String key);

    /**
     * set.
     * 
     * @param key key
     * @param record record
     * @since 0.1.7
     */
    void set(String key, SandboxRecord record);

    /**
     * hdel.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    SandboxRecord hdel(String key);

    /**
     * flushdb.
     * 
     * @return the result
     * @since 0.1.7
     */
    List<SandboxRecord> flushdb();

    /**
     * evictExpired.
     * 
     * @param idleTtlSeconds idleTtlSeconds
     * @param now now
     * @return the result
     * @since 0.1.7
     */
    List<SandboxRecord> evictExpired(int idleTtlSeconds, double now);
}
