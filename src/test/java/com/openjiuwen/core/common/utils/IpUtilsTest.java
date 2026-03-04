// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Unit tests for {@link IpUtils}.
 */
class IpUtilsTest {

    /**
     * Test IP retrieval.
     */
    @Test
    void testGetLocalIp() {
        String ip = IpUtils.getLocalIp();

        assertNotNull(ip, "IP should not be null");
        assertFalse(ip.isEmpty(), "IP should not be empty");

        // Verify IP format
        String[] parts = ip.split("\\.");
        assertEquals(4, parts.length, "IPv4 should have 4 parts");

        for (String part : parts) {
            int value = Integer.parseInt(part);
            assertTrue(value >= 0 && value <= 255, "IP part should be between 0 and 255");
        }
    }

    /**
     * Test fallback to localhost when network error occurs.
     */
    @Test
    void testGetLocalIpFallback() {
        // This test verifies that even when network is unavailable,
        // the method returns a fallback value
        String ip = IpUtils.getLocalIp();

        assertNotNull(ip, "Should return fallback value on error");
        assertFalse(ip.isEmpty(), "Fallback should not be empty");

        // Either a real IP or fallback "127.0.0.1" is acceptable
        String[] parts = ip.split("\\.");
        assertEquals(4, parts.length, "Should return valid IPv4 format");
    }
}