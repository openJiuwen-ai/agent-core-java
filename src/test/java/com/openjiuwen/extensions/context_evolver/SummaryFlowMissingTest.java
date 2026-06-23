/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.core.MemoryPersistenceHelper;
import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.op.SequentialOp;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <p>Mirrors Python's {@code test_summary_flow} module in
 * {@code tests/unit_tests/extensions/context_evolver/test_summary_flow.py}.</p>
 */
class SummaryFlowMissingTest {

    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: "
            + "Live context_evolver API tests require RUN_CONTEXT_EVOLVER_API_TESTS=1 "
            + "and a valid API_KEY in .env.";

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void rbSummarizeAndRetrieveSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void remeSummarizeAndRetrieveSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void aceSummarizeAndRetrieveSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void refconSummarizeAndRetrieveSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void divconSummarizeAndRetrieveSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void algorithmNormalizeAceSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void algorithmNormalizeReasoningBankSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void algorithmNormalizeReMeSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void algorithmNormalizeRefConSkipped() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void algorithmNormalizeDivConSkipped() {
    }

    @Test
    void noPersistOpWhenPersistTypeNone() {
        for (String algorithm : List.of("ACE", "RB", "REME", "REFCON", "DIVCON")) {
            TaskMemoryService service = service(algorithm, "");

            assertNull(service.getPersistenceHelper(), "unexpected helper for " + algorithm);
            assertEquals("UpdateVectorStoreOp", lastOp(service).getClass().getSimpleName());
        }
    }

    @Test
    void persistOpAppendedWhenPersistTypeJsonAce() {
        TaskMemoryService service = service("ACE", "json");

        assertPersistence(service, "json");
        assertEquals("UpdateVectorStoreOp", lastOp(service).getClass().getSimpleName());
    }

    @Test
    void persistOpAppendedWhenPersistTypeJsonReasoningBank() {
        TaskMemoryService service = service("RB", "json");

        assertPersistence(service, "json");
        assertEquals("UpdateVectorStoreOp", lastOp(service).getClass().getSimpleName());
    }

    @Test
    void persistOpAppendedWhenPersistTypeJsonReMe() {
        TaskMemoryService service = service("REME", "json");

        assertPersistence(service, "json");
        assertEquals("UpdateVectorStoreOp", lastOp(service).getClass().getSimpleName());
    }

    @Test
    void persistOpAppendedRefCon() {
        TaskMemoryService service = service("REFCON", "json");

        assertPersistence(service, "json");
        assertEquals("UpdateVectorStoreOp", lastOp(service).getClass().getSimpleName());
    }

    @Test
    void persistOpAppendedDivCon() {
        TaskMemoryService service = service("DIVCON", "json");

        assertPersistence(service, "json");
        assertEquals("UpdateVectorStoreOp", lastOp(service).getClass().getSimpleName());
    }

    @Test
    void persistPathForwardedToOp() {
        TaskMemoryService service = service("ACE", "json", "./data/{algo_name}/{user_id}.json");

        assertNotNull(service.getPersistenceHelper());
        assertEquals("./data/{algo_name}/{user_id}.json", service.getPersistenceHelper().getPersistPath());
    }

    @Test
    void reconfigurePreservesPersistType() {
        TaskMemoryService service = service("ACE", "json");

        service.reconfigure("RB");

        assertPersistence(service, "json");
        assertEquals("ReasoningBank", service.getSummaryAlgorithm());
        assertEquals("ReasoningBank", service.getRetrievalAlgorithm());
        assertEquals("UpdateVectorStoreOp", lastOp(service).getClass().getSimpleName());
    }

    @Test
    void reconfigureWithoutPersistNoPersistOp() {
        TaskMemoryService service = service("ACE", "");

        service.reconfigure("REME");

        assertNull(service.getPersistenceHelper());
        assertEquals("ReMe", service.getSummaryAlgorithm());
        assertEquals("ReMe", service.getRetrievalAlgorithm());
        assertEquals("UpdateVectorStoreOp", lastOp(service).getClass().getSimpleName());
    }

    @Test
    void allAlgorithmNormalizations() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("ACE", "ACE");
        expected.put("RB", "ReasoningBank");
        expected.put("REASONINGBANK", "ReasoningBank");
        expected.put("REME", "ReMe");
        expected.put("REFCON", "RefCon");
        expected.put("DIVCON", "DivCon");

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), TaskMemoryService.normalizeAlgoName(entry.getKey()));
        }
    }

    private static TaskMemoryService service(String algorithm, String persistType) {
        return service(algorithm, persistType, "./memories/{algo_name}/{user_id}.json");
    }

    private static TaskMemoryService service(String algorithm, String persistType, String persistPath) {
        Map<String, Object> snapshot = Config.snapshot();
        try {
            Config.restore(new LinkedHashMap<>());
            Config.setValue("API_KEY", "test-key");
            Config.delete("PERSIST_TYPE");
            return new TaskMemoryService(
                    "gpt-test",
                    "emb-test",
                    "test-key",
                    algorithm,
                    algorithm,
                    null,
                    persistType,
                    persistPath,
                    null,
                    19530,
                    null
            );
        } finally {
            Config.restore(snapshot);
        }
    }

    private static BaseOp lastOp(TaskMemoryService service) {
        BaseOp flow = service.getSummaryFlow();
        if (flow instanceof SequentialOp sequentialOp) {
            List<BaseOp> ops = sequentialOp.getOps();
            return ops.get(ops.size() - 1);
        }
        return flow;
    }

    private static void assertPersistence(TaskMemoryService service, String persistType) {
        MemoryPersistenceHelper helper = service.getPersistenceHelper();
        assertNotNull(helper);
        assertEquals(persistType, helper.getPersistType());
    }
}
