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
 * 哈希工具类 - 用于从API凭证生成确定性的SHA-256密钥。
 * Hash utility — generates deterministic SHA-256 keys from API credentials.
 * 
 * @since 0.1.7
 */
public final class HashUtil {
    /**
     * HashUtil.
     * 
     * @since 0.1.7
     */
    private HashUtil() {
    }

    /**
     * Generate a deterministic SHA-256 hex key from arbitrary input strings.
     * Inputs are sorted before hashing to ensure order-independent results.
     * 
     * @param parts the input strings to hash
     * @return hex-encoded SHA-256 hash
     * @since 0.1.7
     */
    public static String generateKey(String... parts) {
        Arrays.sort(parts);
        String combined = String.join("", parts);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available in this environment", e);
        }
    }
}
