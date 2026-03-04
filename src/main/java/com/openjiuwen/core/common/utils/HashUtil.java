// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Utility class for generating hash keys.
 *
 * <p>This class provides methods to generate SHA256 hash keys from
 * API credentials and other configuration values.</p>
 */
public final class HashUtil {

    private static final String DEFAULT_MODEL_PROVIDER = "openai";

    private HashUtil() {
        // Prevent instantiation
    }

    /**
     * Generate a SHA256 hash key from the provided parameters.
     *
     * <p>The input values are sorted alphabetically before hashing to ensure
     * consistent results regardless of input order.</p>
     *
     * @param apiKey the API key
     * @param apiBase the API base URL
     * @param modelProvider the model provider name (default: "openai")
     * @return the SHA256 hash as a hexadecimal string
     * @throws NullPointerException if apiKey or apiBase is null
     */
    public static String generateKey(String apiKey, String apiBase, String modelProvider) {
        String[] parts = {apiKey, apiBase, modelProvider};
        Arrays.sort(parts);
        String combined = String.join("", parts);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generate a SHA256 hash key from the provided parameters using the default model provider.
     *
     * <p>Uses "openai" as the default model provider.</p>
     *
     * @param apiKey the API key
     * @param apiBase the API base URL
     * @return the SHA256 hash as a hexadecimal string
     * @throws NullPointerException if apiKey or apiBase is null
     */
    public static String generateKey(String apiKey, String apiBase) {
        return generateKey(apiKey, apiBase, DEFAULT_MODEL_PROVIDER);
    }

    /**
     * Convert a byte array to a hexadecimal string.
     *
     * @param bytes the byte array to convert
     * @return the hexadecimal string representation
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}