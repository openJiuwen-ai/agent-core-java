/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient;

import java.util.Iterator;
import java.util.Map;

/**
 * Remote-client abstraction.
 */
public interface RemoteClient {

    void start();

    void stop();

    boolean isStarted();

    default boolean isStopped() {
        return !isStarted();
    }

    Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception;

    Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception;
}
