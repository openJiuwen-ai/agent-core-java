  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.Map;

/**
 * Base class for Milvus vector field configurations.
 * <p>
 * Not intended for direct use. Use specific index classes like:
 * MilvusAUTO, MilvusHNSW, MilvusIVF, MilvusFLAT, or MilvusSCANN instead.
 */
public abstract class MilvusVectorField extends VectorField {

    @Override
    public String getDatabaseType() {
        return "milvus";
    }

    /**
     * Validate scalar quantization (SQ) options for index construction.
     *
     * @return empty string if valid, otherwise semicolon-prefixed error messages
     */
    protected static String validateSqConstruct(Map<String, Object> extraConstruct) {
        StringBuilder err = new StringBuilder();
        if (extraConstruct != null && !extraConstruct.isEmpty()) {
            Object refine = extraConstruct.get("refine");
            Object refineType = extraConstruct.get("refine_type");
            if (refine != null && !(refine instanceof Boolean)) {
                err.append("; \"refine\" must be a bool value");
            }
            if (Boolean.TRUE.equals(refine) && refineType != null) {
                String rt = refineType.toString();
                if (!("SQ6".equals(rt) || "SQ8".equals(rt) || "FP16".equals(rt)
                        || "BF16".equals(rt) || "FP32".equals(rt))) {
                    err.append("; if set, \"refine_type\" must be one of [\"SQ6\", \"SQ8\", \"FP16\", \"BF16\", \"FP32\"]");
                }
            }
        }
        return err.toString();
    }

    /**
     * Validate scalar quantization (SQ) options for search stage.
     */
    protected static String validateSqSearch(Map<String, Object> extraSearch) {
        StringBuilder err = new StringBuilder();
        if (extraSearch != null && !extraSearch.isEmpty()) {
            Object refineK = extraSearch.getOrDefault("refine_k", 1.0);
            if (!(refineK instanceof Number) || ((Number) refineK).doubleValue() < 1) {
                err.append("; \"refine_k\" must be float >= 1");
            }
        }
        return err.toString();
    }

    /**
     * Validate product quantization (PQ) options for index construction.
     */
    protected static String validatePqConstruct(Map<String, Object> extraConstruct) {
        StringBuilder err = new StringBuilder();
        if (extraConstruct != null && !extraConstruct.isEmpty()) {
            Object m = extraConstruct.get("m");
            if (m != null && (!(m instanceof Integer) || (int) m < 1 || (int) m > 65536)) {
                err.append("; \"m\" must be either null or int in range [1, 65536]");
            }
            Object nbits = extraConstruct.getOrDefault("nbits", 8);
            if (!(nbits instanceof Integer) || (int) nbits < 1 || (int) nbits > 24) {
                err.append("; \"nbits\" must be int in range [1, 24]");
            }
        }
        return err.toString();
    }
}
