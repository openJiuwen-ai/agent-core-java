/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import java.security.SecureRandom;

/**
 * Generates unique IDs for memory data.
 * Corresponds to Python: manage/mem_model/data_id_manager.py
 *
 * <p>ID format: 24 hex chars (12 bytes) = 6 bytes timestamp + 3 bytes random + 3 bytes user hash
 */
public class DataIdManager {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generate next unique ID for given user.
     *
     * @param userId user ID for hash component
     * @return 24-character hex string
     */
    public String generateNextId(String userId) {
        // 6 bytes timestamp (milliseconds, masked to 48 bits)
        long timestamp = System.currentTimeMillis() & 0xFFFFFFFFFFFFL;
        byte[] tBytes = new byte[6];
        for (int i = 5; i >= 0; i--) {
            tBytes[i] = (byte) (timestamp & 0xFF);
            timestamp >>>= 8;
        }

        // 3 bytes random
        byte[] rBytes = new byte[3];
        SECURE_RANDOM.nextBytes(rBytes);

        // 3 bytes user hash (lower 24 bits of hash)
        int userHash = userId.hashCode() & 0xFFFFFF;
        byte[] hBytes = new byte[3];
        hBytes[0] = (byte) ((userHash >> 16) & 0xFF);
        hBytes[1] = (byte) ((userHash >> 8) & 0xFF);
        hBytes[2] = (byte) (userHash & 0xFF);

        // Combine: timestamp + random + hash = 12 bytes = 24 hex chars
        byte[] raw = new byte[12];
        System.arraycopy(tBytes, 0, raw, 0, 6);
        System.arraycopy(rBytes, 0, raw, 6, 3);
        System.arraycopy(hBytes, 0, raw, 9, 3);

        return bytesToHex(raw);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

