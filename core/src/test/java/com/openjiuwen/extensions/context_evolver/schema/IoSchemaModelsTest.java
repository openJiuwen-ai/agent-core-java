/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's io_schema regression surface in
 * {@code openjiuwen/extensions/context_evolver/schema/io_schema.py}.
 */
class IoSchemaModelsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aceMemoryRoundTripsThroughVectorNode() {
        ACEMemory memory = new ACEMemory();
        memory.setWorkspaceId("ws");
        memory.setId("mem-1");
        memory.setSection("python");
        memory.setContent("Use lru_cache for memoization");
        memory.setHelpful(3);
        memory.setHarmful(1);
        memory.setNeutral(2);
        memory.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        memory.setUpdatedAt(Instant.parse("2024-01-02T00:00:00Z"));

        VectorNode node = memory.toVectorNode();
        ACEMemory restored = ACEMemory.fromVectorNode(node);

        assertEquals("ace_ws_mem-1", node.getId());
        assertEquals("Use lru_cache for memoization", node.getContent());
        assertEquals("mem-1", restored.getId());
        assertEquals("python", restored.getSection());
        assertEquals(3, restored.getHelpful());
        assertEquals("ws", restored.getWorkspaceId());
    }

    @Test
    void reasoningBankMemoryRoundTripsItemsAndLabel() {
        ReasoningBankMemoryItem item = new ReasoningBankMemoryItem("Caching", "Memoization", "Use functools.lru_cache");
        ReasoningBankMemory memory = new ReasoningBankMemory();
        memory.setWorkspaceId("rb");
        memory.setQuery("How to implement caching?");
        memory.setMemory(List.of(item));
        memory.setLabel(Boolean.TRUE);

        ReasoningBankMemory restored = ReasoningBankMemory.fromVectorNode(memory.toVectorNode());

        assertEquals("rb", restored.getWorkspaceId());
        assertEquals("How to implement caching?", restored.getQuery());
        assertEquals(Boolean.TRUE, restored.getLabel());
        assertEquals("Caching", restored.getMemory().get(0).getTitle());
    }

    @Test
    void reMeMemoryRoundTripsMetadata() {
        ReMeMemoryMetadata metadata = new ReMeMemoryMetadata();
        metadata.setTags(List.of("python", "cache"));
        metadata.setStepType("implementation");
        metadata.setToolsUsed(List.of("functools"));
        metadata.setConfidence(0.95d);
        metadata.setFreq(5);
        metadata.setUtility(4.0d);

        ReMeMemory memory = new ReMeMemory();
        memory.setWorkspaceId("reme");
        memory.setWhenToUse("When implementing caching in Python");
        memory.setContent("Use functools.lru_cache decorator");
        memory.setScore(0.8d);
        memory.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        memory.setUpdatedAt(Instant.parse("2024-01-02T00:00:00Z"));
        memory.setMetadata(metadata);

        ReMeMemory restored = ReMeMemory.fromVectorNode(memory.toVectorNode());

        assertEquals("reme", restored.getWorkspaceId());
        assertEquals("When implementing caching in Python", restored.getWhenToUse());
        assertEquals(0.8d, restored.getScore());
        assertEquals(List.of("python", "cache"), restored.getMetadata().getTags());
        assertEquals("implementation", restored.getMetadata().getStepType());
    }

    @Test
    void requestDtosSerializeSnakeCaseFields() throws Exception {
        ACESummarizeRequest aceRequest = new ACESummarizeRequest();
        aceRequest.setQuery("q");
        aceRequest.setTrajectories(List.of("t1"));
        aceRequest.setGroundTruth("gt");

        ReMeRetrieveRequest reMeRequest = new ReMeRetrieveRequest();
        reMeRequest.setQuery("cache");

        Map<?, ?> aceJson = objectMapper.readValue(objectMapper.writeValueAsString(aceRequest), Map.class);
        Map<?, ?> reMeJson = objectMapper.readValue(objectMapper.writeValueAsString(reMeRequest), Map.class);

        assertEquals("gt", aceJson.get("ground_truth"));
        assertEquals(10, reMeJson.get("topk_retrieval"));
        assertEquals(5, reMeJson.get("topk_rerank"));
    }

    @Test
    void genericResponsesConvertTypedPayloadsToMaps() {
        ACEMemory aceMemory = new ACEMemory();
        aceMemory.setId("mem-1");
        aceMemory.setSection("python");
        aceMemory.setContent("Use lru_cache");
        aceMemory.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        aceMemory.setUpdatedAt(Instant.parse("2024-01-01T00:00:00Z"));

        SummarizeResponse summarizeResponse = new SummarizeResponse("success", List.of(aceMemory));
        RetrieveResponse retrieveResponse = new RetrieveResponse(
                "success",
                "When to use: cache",
                List.of(new ReMeRetrievedMemory("When caching", "Use lru_cache"))
        );

        Object summarizeMemory = summarizeResponse.toMap().get("memory");
        Object retrievedMemory = retrieveResponse.toMap().get("retrieved_memory");

        assertInstanceOf(List.class, summarizeMemory);
        assertInstanceOf(List.class, retrievedMemory);
        assertEquals("mem-1", ((Map<?, ?>) ((List<?>) summarizeMemory).get(0)).get("id"));
        assertEquals("When caching", ((Map<?, ?>) ((List<?>) retrievedMemory).get(0)).get("when_to_use"));
    }

    @Test
    void oursTypesReuseReMeContracts() {
        OursMemory memory = new OursMemory();
        memory.setWhenToUse("When comparing trajectories");
        memory.setContent("Prefer higher-utility memories");

        OursRetrievedMemory retrieved = new OursRetrievedMemory("When comparing trajectories", "Prefer higher-utility memories");

        assertNotNull(memory.toVectorNode());
        assertEquals("When comparing trajectories", retrieved.getWhenToUse());
        assertTrue(memory instanceof ReMeMemory);
    }
}
