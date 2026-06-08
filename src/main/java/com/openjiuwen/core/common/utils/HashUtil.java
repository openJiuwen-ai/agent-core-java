/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Mirrors Python's {@code generate_key} in
 * {@code openjiuwen/core/common/utils/hash_util.py}.
 */
public final class HashUtil {

    private HashUtil() {
    }

    public static String generateKey(String apiKey, String apiBase) {
        return generateKey(apiKey, apiBase, "openai");
    }

    public static String generateKey(String apiKey, String apiBase, String modelProvider) {
        String[] parts = {apiKey, apiBase, modelProvider};
        Arrays.sort(parts);
        String combined = String.join("", parts);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(combined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
