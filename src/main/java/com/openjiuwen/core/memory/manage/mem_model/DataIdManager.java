/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Generates 12-byte memory identifiers from timestamp, random bytes, and user hash.
 *
 * <p>Mirrors Python's {@code DataIdManager} in
 * {@code openjiuwen/core/memory/manage/mem_model/data_id_manager.py}.</p>
 */
public class DataIdManager {
    private final SecureRandom random = new SecureRandom();

    public CompletableFuture<String> generateNextId(String userId) {
        long timestamp = System.currentTimeMillis() & 0xFFFFFFFFFFFFL;
        byte[] randomBytes = new byte[3];
        random.nextBytes(randomBytes);

        int userHash = Objects.hashCode(userId) & 0xFFFFFF;
        ByteBuffer raw = ByteBuffer.allocate(12);
        byte[] timestampBytes = ByteBuffer.allocate(Long.BYTES).putLong(timestamp).array();
        byte[] hashBytes = ByteBuffer.allocate(Integer.BYTES).putInt(userHash).array();

        raw.put(timestampBytes, 2, 6);
        raw.put(randomBytes);
        raw.put(hashBytes, 1, 3);
        return CompletableFuture.completedFuture(toHex(raw.array()));
    }

    private static String toHex(byte[] raw) {
        StringBuilder builder = new StringBuilder(raw.length * 2);
        for (byte value : raw) {
            builder.append(Character.forDigit((value >>> 4) & 0x0F, 16));
            builder.append(Character.forDigit(value & 0x0F, 16));
        }
        return builder.toString();
    }
}
