/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Common retrieval utilities.
 * 
 * <p>Mirrors Python's openjiuwen.core.retrieval.utils.common.py.</p>
 */
public final class CommonUtils {

    private CommonUtils() {
    }

    public static <T, K> List<T> deduplicate(Iterable<T> data, Function<T, K> keyFn) {
        Set<K> seen = new HashSet<>();
        List<T> result = new ArrayList<>();
        if (data == null) {
            return result;
        }
        for (T item : data) {
            K key = keyFn.apply(item);
            if (seen.add(key)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Create a Milvus connection alias string.
     * 
     * <p>Mirrors Python's create_milvus_alias function.</p>
     *
     * @param alias Existing alias (if provided, returned directly)
     * @param uri   Milvus URI
     * @param user  Username
     * @param token Authentication token
     * @return Generated alias string
     */
    public static String createMilvusAlias(String alias, String uri, String user, String token) {
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        
        String authInfo = (user != null && !user.isBlank()) ? user : "noauth";
        
        if (token != null && !token.isBlank()) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(token.getBytes());
                StringBuilder hexString = new StringBuilder();
                for (byte b : digest) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }
                authInfo = hexString.toString();
            } catch (NoSuchAlgorithmException e) {
                // MD5 not available, use original authInfo
            }
        }
        
        // Build alias: kb-{uri}-{auth}
        StringBuilder sb = new StringBuilder("kb");
        if (uri != null && !uri.isBlank()) {
            sb.append("-").append(uri);
        }
        sb.append("-").append(authInfo);
        
        return sb.toString();
    }

    /**
     * Create a Milvus connection alias with default parameters.
     *
     * @param uri   Milvus URI
     * @return Generated alias string
     */
    public static String createMilvusAlias(String uri) {
        return createMilvusAlias(null, uri, null, null);
    }
}
