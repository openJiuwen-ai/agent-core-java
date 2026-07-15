/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.HashMap;
import java.util.Map;

/**
 * Hierarchical Navigable Small World (HNSW) index configuration for Milvus.
 * <p>
 * Builds a multi-layer graph structure for efficient ANN search.
 * Supports optional quantization variants: SQ, PQ, PRQ.
 * 
 * @since 0.1.7
 */
public class MilvusHNSW extends MilvusVectorField {
    private int m = 30;
    private int efConstruction = 360;
    private Float efSearchFactor;
    private String variant;

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> extraConstruct = new HashMap<>();

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> extraSearch = new HashMap<>();

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getIndexType() {
        return "hnsw";
    }

    /**
     * getM.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getM() {
        return m;
    }

    /**
     * setM.
     * 
     * @param m m
     * @since 0.1.7
     */
    public void setM(int m) {
        if (m < 2 || m > 2048) {
            throw new IllegalArgumentException("M must be in range [2, 2048]");
        }
        this.m = m;
    }

    /**
     * getEfConstruction.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getEfConstruction() {
        return efConstruction;
    }

    /**
     * setEfConstruction.
     * 
     * @param efConstruction efConstruction
     * @since 0.1.7
     */
    public void setEfConstruction(int efConstruction) {
        if (efConstruction < 1) {
            throw new IllegalArgumentException("efConstruction must be >= 1");
        }
        this.efConstruction = efConstruction;
    }

    /**
     * getEfSearchFactor.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Float getEfSearchFactor() {
        return efSearchFactor;
    }

    /**
     * setEfSearchFactor.
     * 
     * @param efSearchFactor efSearchFactor
     * @since 0.1.7
     */
    public void setEfSearchFactor(Float efSearchFactor) {
        if (efSearchFactor != null && efSearchFactor < 1) {
            throw new IllegalArgumentException("efSearchFactor must be >= 1");
        }
        this.efSearchFactor = efSearchFactor;
    }

    /**
     * getVariant.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getVariant() {
        return variant;
    }

    /**
     * setVariant.
     * 
     * @param variant variant
     * @since 0.1.7
     */
    public void setVariant(String variant) {
        if (variant != null && !("SQ".equals(variant) || "PQ".equals(variant) || "PRQ".equals(variant))) {
            throw new IllegalArgumentException("variant must be one of: SQ, PQ, PRQ");
        }
        this.variant = variant;
    }

    /**
     * getExtraConstruct.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getExtraConstruct() {
        return extraConstruct;
    }

    /**
     * setExtraConstruct.
     * 
     * @param extraConstruct extraConstruct
     * @since 0.1.7
     */
    public void setExtraConstruct(Map<String, Object> extraConstruct) {
        this.extraConstruct = extraConstruct != null ? extraConstruct : new HashMap<>();
    }

    /**
     * getExtraSearch.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getExtraSearch() {
        return extraSearch;
    }

    /**
     * setExtraSearch.
     * 
     * @param extraSearch extraSearch
     * @since 0.1.7
     */
    public void setExtraSearch(Map<String, Object> extraSearch) {
        this.extraSearch = extraSearch != null ? extraSearch : new HashMap<>();
    }

    /**
     * Validate extra_construct and extra_search parameters based on variant.
     * 
     * @since 0.1.7
     */
    public void validate() {
        if (variant == null) {
            return;
        }

        StringBuilder errMsg = new StringBuilder();
        switch (variant) {
            case "SQ": {
                Object sqType = extraConstruct.getOrDefault("sq_type", "SQ8");
                String st = sqType.toString();
                if (!("SQ4U".equals(st) || "SQ6".equals(st) || "SQ8".equals(st) || "FP16".equals(st)
                        || "BF16".equals(st))) {
                    errMsg.append("; \"sq_type\" must be one of [\"SQ4U\", \"SQ6\", \"SQ8\", \"FP16\", \"BF16\"]");
                }
                errMsg.append(validateSqConstruct(extraConstruct));
                break;
            }
            case "PQ":
                errMsg.append(validatePqConstruct(extraConstruct));
                errMsg.append(validateSqConstruct(extraConstruct));
                errMsg.append(validateSqSearch(extraSearch));
                break;
            case "PRQ": {
                Object nrq = extraConstruct.getOrDefault("nrq", 2);
                errMsg.append(validatePqConstruct(extraConstruct));
                if (!(nrq instanceof Integer) || (int) nrq < 1 || (int) nrq > 16) {
                    errMsg.append("; \"nrq\" must be int in range [1, 16]");
                }
                errMsg.append(validateSqConstruct(extraConstruct));
                errMsg.append(validateSqSearch(extraSearch));
                break;
            }
            default:
                break;
        }

        if (errMsg.length() > 0) {
            String msg = errMsg.toString();
            if (msg.startsWith("; ")) {
                msg = msg.substring(2);
            }
            throw new IllegalArgumentException(
                    "MilvusHNSW with " + variant + " variant has invalid extra arguments: " + msg);
        }
    }

    /**
     * toDict.
     * 
     * @param stage stage
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> toDict(String stage) {
        Map<String, Object> result = new HashMap<>();
        if (STAGE_CONSTRUCT.equals(stage)) {
            result.put("M", m);
            result.put("efConstruction", efConstruction);
            result.put("extra_construct", new HashMap<>(extraConstruct));
        } else if (STAGE_SEARCH.equals(stage)) {
            if (efSearchFactor != null) {
                result.put("efSearchFactor", efSearchFactor);
            }
            result.put("extra_search", new HashMap<>(extraSearch));
        }
        return finalizeDict(result, stage);
    }
}
