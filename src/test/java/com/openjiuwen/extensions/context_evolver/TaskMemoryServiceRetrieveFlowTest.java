/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests.unit_tests.extensions.context_evolver.test_retrieve_flow} in
 * {@code tests/unit_tests/extensions/context_evolver/test_retrieve_flow.py}.
 */
class TaskMemoryServiceRetrieveFlowTest {

    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: "
            + "Live context_evolver API tests require RUN_CONTEXT_EVOLVER_API_TESTS=1 "
            + "and a valid API_KEY in .env.";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void addAndRetrieveMemoryAceSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void addAndRetrieveMemoryReasoningBankSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void addAndRetrieveMemoryReMeSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void addAndRetrieveMemoryRefConSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void addAndRetrieveMemoryDivConSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void retrieveWithoutMemoriesAceSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void retrieveWithoutMemoriesReasoningBankSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void retrieveWithoutMemoriesReMeSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void retrieveWithoutMemoriesRefConSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void retrieveWithoutMemoriesDivConSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void playbookOperationsAceSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void playbookOperationsReasoningBankSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void playbookOperationsReMeSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void playbookOperationsRefConSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void playbookOperationsDivConSkipped() {
    }

    @Test
    void aceNormalized() {
        assertEquals("ACE", TaskMemoryService.normalizeAlgoName("ACE"));
    }

    @Test
    void rbNormalizedToReasoningBank() {
        assertEquals("ReasoningBank", TaskMemoryService.normalizeAlgoName("RB"));
    }

    @Test
    void reasoningBankNormalized() {
        assertEquals("ReasoningBank", TaskMemoryService.normalizeAlgoName("REASONINGBANK"));
    }

    @Test
    void remeNormalized() {
        assertEquals("ReMe", TaskMemoryService.normalizeAlgoName("REME"));
    }

    @Test
    void refconNormalized() {
        assertEquals("RefCon", TaskMemoryService.normalizeAlgoName("REFCON"));
    }

    @Test
    void divconNormalized() {
        assertEquals("DivCon", TaskMemoryService.normalizeAlgoName("DIVCON"));
    }

    @Test
    void invalidAlgorithmRaises() {
        assertThrows(IllegalArgumentException.class, () -> TaskMemoryService.normalizeAlgoName("INVALID_ALGO"));
    }

    @Test
    void explicitAceAlgorithm() {
        TaskMemoryService service = service("ACE", "ACE");

        assertEquals("ACE", service.getSummaryAlgorithm());
        assertEquals("ACE", service.getRetrievalAlgorithm());
    }

    @Test
    void retrievalAlgoStoredCorrectly() {
        Map<String, String> expectations = new LinkedHashMap<>();
        expectations.put("ACE", "ACE");
        expectations.put("RB", "ReasoningBank");
        expectations.put("REME", "ReMe");
        expectations.put("REFCON", "RefCon");
        expectations.put("DIVCON", "DivCon");

        for (Map.Entry<String, String> entry : expectations.entrySet()) {
            TaskMemoryService service = service(entry.getKey(), entry.getKey());
            assertEquals(entry.getValue(), service.getRetrievalAlgorithm(), "retrieval failed for " + entry.getKey());
            assertEquals(entry.getValue(), service.getSummaryAlgorithm(), "summary failed for " + entry.getKey());
        }
    }

    @Test
    void persistTypeJsonByDefault() {
        TaskMemoryService service = service("ACE", "ACE");

        assertEquals("json", service.getPersistType());
    }

    @Test
    void persistTypeJsonStored() {
        TaskMemoryService service = service("ACE", "ACE", "json",
                "./tmp/{algo_name}/{user_id}.json", null, 19530, null);

        assertEquals("json", service.getPersistType());
        assertEquals("./tmp/{algo_name}/{user_id}.json", service.getPersistPath());
    }

    @Test
    void persistTypeAutoStored() {
        TaskMemoryService service = service("ACE", "ACE", "auto", null, null, 19530, null);

        assertEquals("auto", service.getPersistType());
    }

    @Test
    void milvusParamsStored() {
        TaskMemoryService service = service("ACE", "ACE", "milvus", null,
                "milvus-host", 9999, "my_coll");

        assertEquals("milvus-host", service.getMilvusHost());
        assertEquals(9999, service.getMilvusPort());
        assertEquals("my_coll", service.getMilvusCollection());
    }

    @Test
    void retrieveFlowCreatedForEachAlgorithm() {
        for (String algorithm : List.of("ACE", "RB", "REME", "REFCON", "DIVCON")) {
            TaskMemoryService service = service(algorithm, "ACE");
            assertNotNull(service.getRetrieveFlow(), "retrieve_flow is null for " + algorithm);
        }
    }

    @Test
    void summaryFlowCreatedForEachAlgorithm() {
        for (String algorithm : List.of("ACE", "RB", "REME", "REFCON", "DIVCON")) {
            TaskMemoryService service = service("ACE", algorithm);
            assertNotNull(service.getSummaryFlow(), "summary_flow is null for " + algorithm);
        }
    }

    private static TaskMemoryService service(String retrievalAlgo, String summaryAlgo) {
        return service(retrievalAlgo, summaryAlgo, null, null, null, 19530, null);
    }

    private static TaskMemoryService service(String retrievalAlgo,
                                             String summaryAlgo,
                                             String persistType,
                                             String persistPath,
                                             String milvusHost,
                                             int milvusPort,
                                             String milvusCollection) {
        Map<String, Object> snapshot = Config.snapshot();
        try {
            Config.restore(new LinkedHashMap<>());
            Config.setValue("API_KEY", "test-key");
            Config.delete("PERSIST_TYPE");
            return new TaskMemoryService(
                    "gpt-test",
                    "emb-test",
                    "test-key",
                    retrievalAlgo,
                    summaryAlgo,
                    null,
                    persistType,
                    persistPath,
                    milvusHost,
                    milvusPort,
                    milvusCollection
            );
        } finally {
            Config.restore(snapshot);
        }
    }
}
