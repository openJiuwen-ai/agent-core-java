/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors Python's {@code MilvusVectorField} in
 * {@code openjiuwen/core/foundation/store/vector_fields/milvus_fields.py}.
 */
public abstract class MilvusVectorField extends VectorField {

    @Override
    public String getDatabaseType() {
        return "milvus";
    }

    protected static String validateSqConstruct(Map<String, Object> extraConstruct) {
        StringBuilder err = new StringBuilder();
        if (extraConstruct != null && !extraConstruct.isEmpty()) {
            Object refine = extraConstruct.get("refine");
            Object refineType = extraConstruct.get("refine_type");
            if (refine != null && !(refine instanceof Boolean)) {
                err.append("; \"refine\" must be a bool value");
            }
            if (Boolean.TRUE.equals(refine)
                    && refineType != null
                    && !("SQ6".equals(refineType)
                    || "SQ8".equals(refineType)
                    || "FP16".equals(refineType)
                    || "BF16".equals(refineType)
                    || "FP32".equals(refineType))) {
                err.append("; if set, \"refine_type\" must be one of [\"SQ6\", \"SQ8\", \"FP16\", \"BF16\", \"FP32\"]");
            }
        }
        return err.toString();
    }

    protected static String validateSqSearch(Map<String, Object> extraSearch) {
        StringBuilder err = new StringBuilder();
        if (extraSearch != null && !extraSearch.isEmpty()) {
            Object refineK = extraSearch.getOrDefault("refine_k", 1.0d);
            if (!(refineK instanceof Number) || ((Number) refineK).doubleValue() < 1) {
                err.append("; \"refine_k\" must be float >= 1");
            }
        }
        return err.toString();
    }

    protected static String validatePqConstruct(Map<String, Object> extraConstruct) {
        StringBuilder err = new StringBuilder();
        if (extraConstruct != null && !extraConstruct.isEmpty()) {
            Object m = extraConstruct.get("m");
            if (m != null && (!(m instanceof Integer) || ((Integer) m) < 1 || ((Integer) m) > 65536)) {
                err.append("; \"m\" must be either None or int in range [1, 65536]");
            }
            Object nbits = extraConstruct.getOrDefault("nbits", 8);
            if (!(nbits instanceof Integer) || ((Integer) nbits) < 1 || ((Integer) nbits) > 24) {
                err.append("; \"nbits\" must be int in range [1, 24]");
            }
        }
        return err.toString();
    }

    protected static void appendOrderedEntries(
            Map<String, Object> target,
            Map<String, Object> extras,
            String... preferredOrder
    ) {
        if (extras == null || extras.isEmpty()) {
            return;
        }
        Set<String> emitted = new LinkedHashSet<>();
        for (String key : preferredOrder) {
            if (extras.containsKey(key)) {
                target.put(key, extras.get(key));
                emitted.add(key);
            }
        }
        for (Map.Entry<String, Object> entry : extras.entrySet()) {
            if (!emitted.contains(entry.getKey())) {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
