/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Mirrors Python's retrieval utility helpers in
 * {@code openjiuwen/core/retrieval/utils/common.py}.
 */
public final class CommonUtils {

    private CommonUtils() {
    }

    public static <T> List<T> deduplicate(Iterable<T> data) {
        return deduplicate(data, Function.identity());
    }

    public static <T, K> List<T> deduplicate(Iterable<T> data, Function<T, K> key) {
        Set<K> seen = new HashSet<>();
        List<T> result = new ArrayList<>();
        for (T item : data) {
            K k = key.apply(item);
            if (seen.add(k)) {
                result.add(item);
            }
        }
        return result;
    }

    public static String createMilvusAlias(String alias, String uri, String user, String token) {
        if (alias != null && !alias.isEmpty()) {
            return alias;
        }
        String authInfo = (user != null && !user.isEmpty()) ? user : "noauth";
        if (token != null) {
            authInfo = md5Hex(token);
        }
        List<String> parts = new ArrayList<>();
        parts.add("kb");
        parts.add(uri);
        parts.add(authInfo);
        return String.join("-", parts);
    }

    public static String createMilvusAlias(String alias, String uri) {
        return createMilvusAlias(alias, uri, "", null);
    }

    private static String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte one : bytes) {
                builder.append(String.format("%02x", one));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("MD5 unavailable", error);
        }
    }
}
