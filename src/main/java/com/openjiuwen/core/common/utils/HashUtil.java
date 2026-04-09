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
 * Hash utility — generates deterministic SHA-256 keys from API credentials.
 */
public final class HashUtil {

    private HashUtil() {
    }

    /**
     * Generate a deterministic SHA-256 hex key from API key, base URL, and model provider.
     *
     * @param apiKey        the API key
     * @param apiBase       the API base URL
     * @param modelProvider the model provider name (default "openai")
     * @return hex-encoded SHA-256 hash
     */
    public static String generateKey(String apiKey, String apiBase, String modelProvider) {
        String[] parts = {apiKey, apiBase, modelProvider};
        Arrays.sort(parts);
        String combined = String.join("", parts);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /** Overload with default modelProvider = "openai". */
    public static String generateKey(String apiKey, String apiBase) {
        return generateKey(apiKey, apiBase, "openai");
    }
}
