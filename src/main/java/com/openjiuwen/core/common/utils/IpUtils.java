/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Mirrors Python's {@code openjiuwen.core.common.utils.ip_utils} in
 * {@code openjiuwen/core/common/utils/ip_utils.py}.
 */
public final class IpUtils {

    private static final String PROBE_HOST = "8.8.8.8";
    private static final int PROBE_PORT = 80;
    private static final String LOOPBACK_IP = "127.0.0.1";

    private IpUtils() {
    }

    public static String getLocalIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName(PROBE_HOST), PROBE_PORT));
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception ignored) {
            return LOOPBACK_IP;
        }
    }
}
