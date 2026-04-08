/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * IP utility — discovers the local (non-loopback) IPv4 address.
 */
public final class IpUtils {

    private IpUtils() {
    }

    /**
     * Get the local available IPv4 address (excluding 127.0.0.1).
     * <p>
     * Uses a UDP socket trick: connects a datagram socket to a public address
     * to reveal the local network interface address.
     *
     * @return local IP address string, or "127.0.0.1" on failure
     */
    public static String getLocalIp() {
        String defaultIp = AppconfigUtils.getDefaultIp();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName(defaultIp), 80));
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
