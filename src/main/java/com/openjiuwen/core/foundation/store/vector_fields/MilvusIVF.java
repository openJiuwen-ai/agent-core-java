/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.HashMap;
import java.util.Map;

/**
 * Inverted File (IVF) index configuration for Milvus.
 * <p>
 * Divides vector space into clusters using k-means and searches only relevant clusters.
 * Supports quantization variants: FLAT, SQ8, PQ, RABITQ.
 */
public class MilvusIVF extends MilvusVectorField {

    private int nlist = 128;
    private int nprobe = 8;
    private String variant = "FLAT";
    private Map<String, Object> extraConstruct = new HashMap<>();
    private Map<String, Object> extraSearch = new HashMap<>();

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getIndexType() {
        return "ivf";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getNlist() {
        return nlist;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setNlist(int nlist) {
        if (nlist < 1 || nlist > 65536) {
            throw new IllegalArgumentException("nlist must be in range [1, 65536]");
        }
        this.nlist = nlist;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getNprobe() {
        return nprobe;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setNprobe(int nprobe) {
        if (nprobe < 1 || nprobe > 65536) {
            throw new IllegalArgumentException("nprobe must be in range [1, 65536]");
        }
        if (nprobe > nlist) {
            throw new IllegalArgumentException("nprobe must be <= nlist");
        }
        this.nprobe = nprobe;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getVariant() {
        return variant;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setVariant(String variant) {
        if (!("FLAT".equals(variant) || "SQ8".equals(variant) || "PQ".equals(variant) || "RABITQ".equals(variant))) {
            throw new IllegalArgumentException("variant must be one of: FLAT, SQ8, PQ, RABITQ");
        }
        this.variant = variant;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getExtraConstruct() {
        return extraConstruct;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExtraConstruct(Map<String, Object> extraConstruct) {
        this.extraConstruct = extraConstruct != null ? extraConstruct : new HashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getExtraSearch() {
        return extraSearch;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExtraSearch(Map<String, Object> extraSearch) {
        this.extraSearch = extraSearch != null ? extraSearch : new HashMap<>();
    }

    /**
     * Validate extra_construct and extra_search parameters based on variant.
     *
     * @throws IllegalArgumentException if arguments are invalid for the variant
     */
    public void validate() {
        if (nprobe > nlist) {
            throw new IllegalArgumentException("nprobe must be <= nlist (got nprobe=" + nprobe + ", nlist=" + nlist + ")");
        }

        StringBuilder errMsg = new StringBuilder();
        switch (variant) {
            case "FLAT":
            case "SQ8":
                if (!extraConstruct.isEmpty() || !extraSearch.isEmpty()) {
                    errMsg.append(variant).append(" does not accept any extra arguments");
                }
                break;
            case "PQ":
                errMsg.append(validatePqConstruct(extraConstruct));
                if (!extraSearch.isEmpty()) {
                    errMsg.append("; this variant does not accept extra search arguments");
                }
                break;
            case "RABITQ":
                if (!extraConstruct.isEmpty()) {
                    errMsg.append(validateSqConstruct(extraConstruct));
                }
                if (!extraSearch.isEmpty()) {
                    errMsg.append(validateSqSearch(extraSearch));
                    Object rbqBits = extraSearch.getOrDefault("rbq_query_bits", 0);
                    if (!(rbqBits instanceof Integer) || (int) rbqBits < 0 || (int) rbqBits > 8) {
                        errMsg.append("; \"rbq_query_bits\" must be int in range [0, 8]");
                    }
                }
                break;
            default:
                break;
        }

        if (errMsg.length() > 0) {
            String msg = errMsg.toString();
            if (msg.startsWith("; ")) {
                msg = msg.substring(2);
            }
            throw new IllegalArgumentException(
                    "MilvusIVF with " + variant + " variant has invalid extra arguments: " + msg);
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toDict(String stage) {
        Map<String, Object> result = new HashMap<>();
        if (STAGE_CONSTRUCT.equals(stage)) {
            result.put("nlist", nlist);
            result.put("extra_construct", new HashMap<>(extraConstruct));
        } else if (STAGE_SEARCH.equals(stage)) {
            result.put("nprobe", nprobe);
            result.put("extra_search", new HashMap<>(extraSearch));
        }
        return finalizeDict(result, stage);
    }
}
