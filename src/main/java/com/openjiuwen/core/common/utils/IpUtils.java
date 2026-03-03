// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Utility class for IP address operations.
 *
 * <p>This class provides methods to retrieve the local IP address.</p>
 */
public final class IpUtils {

    private static final String FALLBACK_IP = "127.0.0.1";

    private IpUtils() {
        // Prevent instantiation
    }

    /**
     * Get the local available IPv4 address (excluding 127.0.0.1 if possible).
     *
     * <p>This method attempts to determine the local IP address by connecting
     * to a remote server (8.8.8.8:80). If network access is unavailable,
     * it returns the fallback address "127.0.0.1".</p>
     *
     * <p>The approach works because the OS will use the interface that can
     * reach the remote address to establish the connection.</p>
     *
     * @return the local IPv4 address, or "127.0.0.1" if unavailable
     */
    public static String getLocalIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 80);
            return socket.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            return FALLBACK_IP;
        }
    }
}