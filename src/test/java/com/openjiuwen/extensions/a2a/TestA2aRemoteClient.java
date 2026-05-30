/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test A2A remote client functionality.
 * <p>
 * Mirrors Python's {@code test_a2a_remote_client.py} in
 * {@code tests/unit_tests/extensions/a2a/test_a2a_remote_client.py}.
 *
 */
class TestA2aRemoteClient {

    /**
     * Test A2ARemoteClient initialization.
     */
    @Nested
    class TestInit {

        @Test
        void testInitSuccess() {
            A2ARemoteClient client = new A2ARemoteClient("http://127.0.0.1:41241");

            assertEquals("http://127.0.0.1:41241", client.getEndpoint());
            assertFalse(client.isConnected());
        }

        @Test
        void testInitWithRemoteUrl() {
            A2ARemoteClient client = new A2ARemoteClient(
                    "https://example.com/a2a",
                    Map.of("name", "a2a-agent"));

            assertEquals("https://example.com/a2a", client.getEndpoint());
            assertEquals("a2a-agent", client.getCard().get("name"));
        }
    }

    /**
     * Test A2ARemoteClient remote methods.
     */
    @Nested
    class TestRemoteMethods {

        @Test
        void testConnectToRemote() {
            A2ARemoteClient client = new A2ARemoteClient("https://example.com/a2a");

            assertTrue(client.connectToRemote());
            assertTrue(client.isConnected());
        }

        @Test
        void testDisconnectFromRemote() {
            A2ARemoteClient client = new A2ARemoteClient("https://example.com/a2a");
            client.connectToRemote();

            client.disconnectFromRemote();

            assertFalse(client.isConnected());
            assertTrue(client.isClosed());
        }
    }
}
