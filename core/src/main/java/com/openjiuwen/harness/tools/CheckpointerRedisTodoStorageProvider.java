/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.store.kv.RedisKVStoreProvider;

import java.util.Map;

/**
 * Borrows the application's default Redis checkpointer store and expiry settings.
 *
 * @since 0.1.15
 */
public final class CheckpointerRedisTodoStorageProvider implements TodoStorageProvider {
    /**
     * Storage type that reuses the default Redis checkpointer.
     */
    public static final String TYPE = "checkpointer_redis";

    @Override
    public String typeName() {
        return TYPE;
    }

    @Override
    public TodoStorage create(Map<String, Object> conf) {
        if (conf != null && (conf.containsKey("sharedKvStore") || conf.containsKey("kvStoreConf")
                || conf.containsKey("kvStoreType") || conf.containsKey("connection"))) {
            throw new IllegalArgumentException(TYPE + " cannot specify an independent KV store or connection");
        }
        if (!(CheckpointerFactory.getCheckpointer() instanceof RedisCheckpointer checkpointer)) {
            throw new IllegalStateException(TYPE + " requires an initialized default RedisCheckpointer");
        }
        return TodoStorageTtl.from(conf, checkpointer.getEffectiveTtl().orElse(null), checkpointer.isRefreshOnRead())
                .create(RedisKVStoreProvider.adapt(checkpointer.getRedisStore()));
    }
}
