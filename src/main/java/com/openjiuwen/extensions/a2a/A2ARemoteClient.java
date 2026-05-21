/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import java.util.*;

/**
 * A2A remote client for communicating with external agents.
 * <p>
 * Mirrors Python's {@code A2ARemoteClient} in
 * {@code openjiuwen.extensions.a2a.a2a_remote_client}.
 */
public class A2ARemoteClient extends A2AClient {

    private final String endpoint;

    public A2ARemoteClient(String endpoint, Map<String, Object> card) {
        super(card);
        this.endpoint = endpoint;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
