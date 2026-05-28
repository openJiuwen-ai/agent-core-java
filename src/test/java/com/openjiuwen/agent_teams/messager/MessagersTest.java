/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_messager}.
 * Tests for Messagers factory and transport backend selection.
 */
class MessagersTest {

    @Test
    void createMessagerReturnsInProcessByDefault() {
        assertInstanceOf(InProcessMessager.class, Messagers.createMessager(new MessagerTransportConfig()));
    }

    @Test
    void createMessagerSupportsPyzmqBackend() {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend("pyzmq");
        assertInstanceOf(PyZmqMessager.class, Messagers.createMessager(config));
    }

    @Test
    void pyzmqTransportFailsClosedOnRealIo() {
        PyZmqMessager messager = new PyZmqMessager(new MessagerTransportConfig());
        assertThrows(UnsupportedOperationException.class,
                () -> messager.unsubscribe("topic:team"));
    }
}
