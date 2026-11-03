/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code MilvusHNSW} in
 * {@code openjiuwen/core/foundation/store/vector_fields/milvus_fields.py}.
 */
public class MilvusHNSW extends MilvusVectorField {

    private int m = 30;
    private int efConstruction = 360;
    private Double efSearchFactor;
    private String variant;
    private Map<String, Object> extraConstruct = new LinkedHashMap<>();
    private Map<String, Object> extraSearch = new LinkedHashMap<>();

    @Override
    public String getIndexType() {
        return "hnsw";
    }

    public int getM() {
        return m;
    }

    public void setM(int m) {
        if (m < 2 || m > 2048) {
            throw new IllegalArgumentException("M must be in range [2, 2048]");
        }
        this.m = m;
    }

    public int getEfConstruction() {
        return efConstruction;
    }

    public void setEfConstruction(int efConstruction) {
        if (efConstruction < 1) {
            throw new IllegalArgumentException("efConstruction must be >= 1");
        }
        this.efConstruction = efConstruction;
    }

    public Double getEfSearchFactor() {
        return efSearchFactor;
    }

    public void setEfSearchFactor(Double efSearchFactor) {
        if (efSearchFactor != null && efSearchFactor < 1) {
            throw new IllegalArgumentException("efSearchFactor must be >= 1");
        }
        this.efSearchFactor = efSearchFactor;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        if (variant != null && !("SQ".equals(variant) || "PQ".equals(variant) || "PRQ".equals(variant))) {
            throw new IllegalArgumentException("variant must be one of: SQ, PQ, PRQ");
        }
        this.variant = variant;
    }

    public Map<String, Object> getExtraConstruct() {
        return extraConstruct;
    }

    public void setExtraConstruct(Map<String, Object> extraConstruct) {
        this.extraConstruct = extraConstruct == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraConstruct);
    }

    public Map<String, Object> getExtraSearch() {
        return extraSearch;
    }

    public void setExtraSearch(Map<String, Object> extraSearch) {
        this.extraSearch = extraSearch == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraSearch);
    }

    @Override
    public Map<String, Object> toDict(String stage) {
        validate();
        Map<String, Object> result = new LinkedHashMap<>();
        if (STAGE_CONSTRUCT.equals(stage)) {
            result.put("M", m);
            result.put("efConstruction", efConstruction);
            appendOrderedEntries(result, extraConstruct, "sq_type", "m", "nbits", "nrq", "refine", "refine_type");
        } else if (STAGE_SEARCH.equals(stage)) {
            if (efSearchFactor != null) {
                result.put("efSearchFactor", efSearchFactor);
            }
            appendOrderedEntries(result, extraSearch, "refine_k", "rbq_query_bits");
        }
        return finalizeDict(result, stage);
    }

    public void validate() {
        if (variant == null) {
            return;
        }
        StringBuilder errMsg = new StringBuilder();
        switch (variant) {
            case "SQ":
                Object sqType = extraConstruct.getOrDefault("sq_type", "SQ8");
                if (!("SQ4U".equals(sqType)
                        || "SQ6".equals(sqType)
                        || "SQ8".equals(sqType)
                        || "FP16".equals(sqType)
                        || "BF16".equals(sqType))) {
                    errMsg.append("; \"sq_type\" must be one of [\"SQ4U\", \"SQ6\", \"SQ8\", \"FP16\", \"BF16\"]");
                }
                errMsg.append(validateSqConstruct(extraConstruct));
                break;
            case "PQ":
                errMsg.append(validatePqConstruct(extraConstruct));
                errMsg.append(validateSqConstruct(extraConstruct));
                errMsg.append(validateSqSearch(extraSearch));
                break;
            case "PRQ":
                Object nrq = extraConstruct.getOrDefault("nrq", 2);
                errMsg.append(validatePqConstruct(extraConstruct));
                if (!(nrq instanceof Integer) || ((Integer) nrq) < 1 || ((Integer) nrq) > 16) {
                    errMsg.append("; \"nrq\" must be int in range [1, 16]");
                }
                errMsg.append(validateSqConstruct(extraConstruct));
                errMsg.append(validateSqSearch(extraSearch));
                break;
            default:
                break;
        }
        if (errMsg.length() > 0) {
            String msg = errMsg.toString();
            if (msg.startsWith("; ")) {
                msg = msg.substring(2);
            }
            throw new IllegalArgumentException("MilvusHNSW with " + variant + " variant has invalid extra arguments: " + msg);
        }
    }
}
