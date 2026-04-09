/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.memory.manage.mem_model;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * Generates unique memory IDs using timestamp + random + user hash.
 */
public class DataIdManager {

    private final SecureRandom random = new SecureRandom();

    /**
     * Generate a unique hex ID based on current time, random bytes, and user ID hash.
     *
     * @param userId the user identifier
     * @return hex string ID (24 chars = 12 bytes)
     */
    public String generateNextId(String userId) {
        long t = System.currentTimeMillis() & 0xFFFFFFFFFFFFL; // 6 bytes
        byte[] r = new byte[3];
        random.nextBytes(r);
        int h = userId.hashCode() & 0xFFFFFF; // 3 bytes

        // Pack: 6 bytes timestamp + 3 bytes random + 3 bytes hash = 12 bytes
        ByteBuffer buf = ByteBuffer.allocate(12);
        // 6-byte timestamp (big-endian, top 2 bytes of long trimmed)
        byte[] tBytes = ByteBuffer.allocate(8).putLong(t).array();
        buf.put(tBytes, 2, 6);
        buf.put(r);
        // 3-byte hash (big-endian, top byte of int trimmed)
        byte[] hBytes = ByteBuffer.allocate(4).putInt(h).array();
        buf.put(hBytes, 1, 3);

        byte[] raw = buf.array();
        StringBuilder sb = new StringBuilder(24);
        for (byte b : raw) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
