// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.vendor_specific;

import com.openjiuwen.core.retrieval.common.RerankerConfig;
import com.openjiuwen.core.retrieval.reranker.StandardReranker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aliyun reranker client mirroring Python's vendor-specific implementation.
 */
public class AliyunReranker extends StandardReranker {

    public static final String END_POINT = "/services/rerank/text-rerank/text-rerank";

    public AliyunReranker() {
        super(new RerankerConfig());
    }

    public AliyunReranker(RerankerConfig config) {
        super(config);
    }

    @Override
    protected Map<String, Object> buildRequestPayload(String query,
                                                      List<String> documents,
                                                      Object instruct,
                                                      Map<String, Object> options) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("return_documents", false);
        parameters.put("top_n", options != null && options.containsKey("top_n")
                ? options.get("top_n")
                : documents.size());
        if (instruct instanceof String instructText && !instructText.isEmpty()) {
            parameters.put("instruct", instructText);
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("documents", documents);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", getModelName());
        payload.put("input", input);
        payload.put("parameters", parameters);
        return payload;
    }

    @Override
    protected String endpoint() {
        return END_POINT;
    }
}
