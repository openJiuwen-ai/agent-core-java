/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import java.util.concurrent.CompletableFuture;

/**
 * Storage contract used by translated checkpointers.
 *
 * <p>Mirrors Python's {@code Storage} in
 * {@code openjiuwen/core/session/checkpointer/base.py}.</p>
 */
public interface Storage {

    CompletableFuture<Void> save(Object session);

    CompletableFuture<Void> recover(Object session, Object inputs);

    CompletableFuture<Void> clear(String sessionId);

    CompletableFuture<Boolean> exists(Object session);
}
