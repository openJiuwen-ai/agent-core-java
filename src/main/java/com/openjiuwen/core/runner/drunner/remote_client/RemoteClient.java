/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.remote_client;

import java.util.Iterator;
import java.util.Map;

/**
 * Remote-client abstraction.
 */
public interface RemoteClient {

    void start();

    void stop();

    Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception;

    Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception;
}
