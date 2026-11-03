/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Remote-client abstraction.
 *
 * <p>Mirrors Python's {@code RemoteClient} in
 * {@code openjiuwen/core/runner/drunner/remote_client/remote_client.py}.</p>
 */
public interface RemoteClient {

    CompletionStage<Void> start();

    CompletionStage<Void> stop();

    boolean isStarted();

    default boolean isStopped() {
        return !isStarted();
    }

    CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, Double timeoutSeconds);

    Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds);
}
