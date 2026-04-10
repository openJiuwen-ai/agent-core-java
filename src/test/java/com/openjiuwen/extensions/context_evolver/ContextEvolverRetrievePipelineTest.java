/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.schema.ACERetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeRetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemoryItem;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankRetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.RetrieveResponse;
import com.openjiuwen.extensions.context_evolver.service.AddMemoryRequest;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextEvolverRetrievePipelineTest {

    private Map<String, Object> configSnapshot;

    @BeforeEach
    void captureState() {
        configSnapshot = Config.snapshot();
        ServiceContext.getInstance().clear();
    }

    @AfterEach
    void restoreState() {
        Config.restore(configSnapshot);
        ServiceContext.getInstance().clear();
    }

    @Test
    void aceRetrieveLoadsAllPlaybookBulletsForTheUser() {
        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "ACE",
            "ACE"
        );

        AddMemoryRequest first = new AddMemoryRequest();
        first.setContent("Always cache the expensive result.");
        first.setSection("cache");
        service.addMemory("user-ace", first).join();

        AddMemoryRequest second = new AddMemoryRequest();
        second.setContent("Prefer immutable DTOs for replayable state.");
        second.setSection("api");
        service.addMemory("user-ace", second).join();

        RetrieveResponse response = service.retrieveResponse("user-ace", "How should I build this?").join();
        assertEquals(2, response.getRetrievedMemory().size());
        assertInstanceOf(ACERetrievedMemory.class, response.getRetrievedMemory().get(0));
        assertTrue(response.getMemoryString().contains("Section: api"));
        assertTrue(response.getMemoryString().contains("Section: cache"));
    }

    @Test
    void reasoningBankRetrieveUsesQuerySearchAndFlattensMemoryItems() {
        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "RB",
            "RB"
        );

        ReasoningBankMemory relevant = new ReasoningBankMemory();
        relevant.setWorkspaceId("user-rb");
        relevant.setQuery("cache python api results");
        relevant.setMemory(List.of(
            new ReasoningBankMemoryItem(
                "Use a cache boundary",
                "Introduce a stable cache key",
                "Derive the cache key from the normalized request."
            ),
            new ReasoningBankMemoryItem(
                "Store invalidation hints",
                "Keep invalidation near the write path",
                "Invalidate cache entries when the backing record changes."
            )
        ));
        VectorNode relevantNode = relevant.toVectorNode();
        relevantNode.setEmbedding(defaultEmbeddingFor(relevant.getQuery()));
        service.getVectorStore().asyncUpsert(relevantNode).join();

        ReasoningBankMemory unrelated = new ReasoningBankMemory();
        unrelated.setWorkspaceId("user-rb");
        unrelated.setQuery("css layout spacing");
        unrelated.setMemory(List.of(
            new ReasoningBankMemoryItem(
                "Use gap",
                "Prefer layout gap properties",
                "Use gap instead of manual child margins."
            )
        ));
        VectorNode unrelatedNode = unrelated.toVectorNode();
        unrelatedNode.setEmbedding(defaultEmbeddingFor(unrelated.getQuery()));
        service.getVectorStore().asyncUpsert(unrelatedNode).join();

        RetrieveResponse response = service.retrieveResponse("user-rb", "How do I cache Python API results?").join();
        assertEquals(2, response.getRetrievedMemory().size());
        ReasoningBankRetrievedMemory first = assertInstanceOf(
            ReasoningBankRetrievedMemory.class,
            response.getRetrievedMemory().get(0)
        );
        ReasoningBankRetrievedMemory second = assertInstanceOf(
            ReasoningBankRetrievedMemory.class,
            response.getRetrievedMemory().get(1)
        );
        assertEquals("Use a cache boundary", first.getTitle());
        assertEquals("Store invalidation hints", second.getTitle());
        assertTrue(response.getMemoryString().contains("Use a cache boundary"));
        assertTrue(response.getMemoryString().contains("Store invalidation hints"));
    }

    @Test
    void reMeRetrieveReranksAndWritesMemoryString() {
        Config.setValue("TOPK_RETRIEVAL", 10);
        Config.setValue("TOPK_RERANK", 2);
        Config.setValue("LLM_RERANK", true);
        Config.setValue("LLM_REWRITE", true);

        TaskMemoryService service = new TaskMemoryService(
            "gpt-5.2",
            "text-embedding-3-small",
            null,
            "ReMe",
            "ReMe"
        );

        AddMemoryRequest cache = new AddMemoryRequest();
        cache.setWhenToUse("When caching Redis API responses");
        cache.setContent("Cache normalized responses under a deterministic Redis key.");
        service.addMemory("user-reme", cache).join();

        AddMemoryRequest invalidation = new AddMemoryRequest();
        invalidation.setWhenToUse("When invalidating Redis caches after writes");
        invalidation.setContent("Invalidate related keys in the same transaction as the write.");
        service.addMemory("user-reme", invalidation).join();

        AddMemoryRequest unrelated = new AddMemoryRequest();
        unrelated.setWhenToUse("When styling a CSS card layout");
        unrelated.setContent("Use gap and clamp for responsive spacing.");
        service.addMemory("user-reme", unrelated).join();

        RetrieveResponse response = service.retrieveResponse(
            "user-reme",
            "How should I cache API responses in Redis?"
        ).join();
        assertEquals(2, response.getRetrievedMemory().size());

        ReMeRetrievedMemory first = assertInstanceOf(ReMeRetrievedMemory.class, response.getRetrievedMemory().get(0));
        ReMeRetrievedMemory second = assertInstanceOf(ReMeRetrievedMemory.class, response.getRetrievedMemory().get(1));
        assertEquals("When caching Redis API responses", first.getWhenToUse());
        assertEquals("When invalidating Redis caches after writes", second.getWhenToUse());
        assertTrue(response.getMemoryString().startsWith("For the current query"));
        assertTrue(response.getMemoryString().contains(first.getContent()));
        assertTrue(response.getMemoryString().contains(second.getContent()));
        assertTrue(!response.getMemoryString().contains("CSS card layout"));
    }

    private static List<Double> defaultEmbeddingFor(String value) {
        int dimensions = 32;
        double[] dense = new double[dimensions];
        String normalized = value != null ? value.toLowerCase(Locale.ROOT) : "";
        String[] tokens = normalized.split("[^a-z0-9]+");
        int previousSlot = -1;

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int slot = Math.floorMod(token.hashCode(), dimensions);
            dense[slot] += 1.0d;
            if (previousSlot >= 0) {
                dense[(previousSlot + slot) % dimensions] += 0.25d;
            }
            previousSlot = slot;
        }

        if (Arrays.stream(dense).allMatch(component -> component == 0.0d)) {
            dense[0] = 1.0d;
        }

        List<Double> result = new ArrayList<>(dimensions);
        for (double component : dense) {
            result.add(component);
        }
        return result;
    }
}
