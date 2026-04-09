/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.extensions.vendor_specific;

import com.openjiuwen.core.retrieval.common.RerankerConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AliyunRerankerTest {

    @Test
    void buildRequestPayloadMatchesPythonShapeAndDefaultTopN() {
        InspectableAliyunReranker reranker = new InspectableAliyunReranker(config());

        Map<String, Object> payload = reranker.inspectPayload(
                "which document matches best",
                List.of("doc-1", "doc-2"),
                null,
                Map.of());

        assertEquals("aliyun-reranker", payload.get("model"));
        assertEquals(List.of("model", "input", "parameters"), List.copyOf(payload.keySet()));

        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) payload.get("input");
        assertEquals("which document matches best", input.get("query"));
        assertEquals(List.of("doc-1", "doc-2"), input.get("documents"));

        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) payload.get("parameters");
        assertEquals(false, parameters.get("return_documents"));
        assertEquals(2, parameters.get("top_n"));
        assertFalse(parameters.containsKey("instruct"));
    }

    @Test
    void buildRequestPayloadPassesThroughStringInstructAndExplicitTopN() {
        InspectableAliyunReranker reranker = new InspectableAliyunReranker(config());
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("top_n", 1);

        Map<String, Object> payload = reranker.inspectPayload(
                "query",
                List.of("doc-1", "doc-2"),
                "   ",
                options);

        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) payload.get("parameters");
        assertEquals(1, parameters.get("top_n"));
        assertEquals("   ", parameters.get("instruct"));
    }

    @Test
    void endpointMatchesAliyunTextRerankApi() {
        InspectableAliyunReranker reranker = new InspectableAliyunReranker(config());

        assertEquals("/services/rerank/text-rerank/text-rerank", reranker.inspectEndpoint());
    }

    private static RerankerConfig config() {
        RerankerConfig config = new RerankerConfig();
        config.setApiBase("https://api.example.com/v1");
        config.setApiKey("test-key");
        config.setModelName("aliyun-reranker");
        return config;
    }

    private static final class InspectableAliyunReranker extends AliyunReranker {
        private InspectableAliyunReranker(RerankerConfig config) {
            super(config);
        }

        private Map<String, Object> inspectPayload(String query,
                                                   List<String> documents,
                                                   Object instruct,
                                                   Map<String, Object> options) {
            return buildRequestPayload(query, documents, instruct, options);
        }

        private String inspectEndpoint() {
            return endpoint();
        }
    }
}
