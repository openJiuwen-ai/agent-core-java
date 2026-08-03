/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code MilvusIVF} in
 * {@code openjiuwen/core/foundation/store/vector_fields/milvus_fields.py}.
 */
public class MilvusIVF extends MilvusVectorField {

    private int nlist = 128;
    private int nprobe = 8;
    private String variant = "FLAT";
    private Map<String, Object> extraConstruct = new LinkedHashMap<>();
    private Map<String, Object> extraSearch = new LinkedHashMap<>();

    @Override
    public String getIndexType() {
        return "ivf";
    }

    public int getNlist() {
        return nlist;
    }

    public void setNlist(int nlist) {
        if (nlist < 1 || nlist > 65536) {
            throw new IllegalArgumentException("nlist must be in range [1, 65536]");
        }
        this.nlist = nlist;
    }

    public int getNprobe() {
        return nprobe;
    }

    public void setNprobe(int nprobe) {
        if (nprobe < 1 || nprobe > 65536) {
            throw new IllegalArgumentException("nprobe must be in range [1, 65536]");
        }
        this.nprobe = nprobe;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        if (!("FLAT".equals(variant) || "SQ8".equals(variant) || "PQ".equals(variant) || "RABITQ".equals(variant))) {
            throw new IllegalArgumentException("variant must be one of: FLAT, SQ8, PQ, RABITQ");
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
            result.put("nlist", nlist);
            appendOrderedEntries(result, extraConstruct, "m", "nbits", "refine", "refine_type");
        } else if (STAGE_SEARCH.equals(stage)) {
            result.put("nprobe", nprobe);
            appendOrderedEntries(result, extraSearch, "refine_k", "rbq_query_bits");
        }
        return finalizeDict(result, stage);
    }

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
                    Object rbqQueryBits = extraSearch.getOrDefault("rbq_query_bits", 0);
                    if (!(rbqQueryBits instanceof Integer)
                            || ((Integer) rbqQueryBits) < 0
                            || ((Integer) rbqQueryBits) > 8) {
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
            throw new IllegalArgumentException("MilvusIVF with " + variant + " variant has invalid extra arguments: " + msg);
        }
    }
}
