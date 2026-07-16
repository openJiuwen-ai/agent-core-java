/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Common retrieval utilities.
 * 
 * @since 0.1.7
 */
public final class CommonUtils {
    /**
     * CommonUtils.
     * 
     * @since 0.1.7
     */
    private CommonUtils() {
    }

    /**
     * deduplicate.
     * 
     * @param data data
     * @param keyFn keyFn
     * @return the result
     * @since 0.1.7
     */
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
}
