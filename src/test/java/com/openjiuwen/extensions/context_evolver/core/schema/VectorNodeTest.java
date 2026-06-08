/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VectorNodeTest {

    @Test
    void toDictAndFromDictMirrorPythonModelDumpBehavior() {
        VectorNode node = new VectorNode(
            "vec-1",
            "memory fragment",
            List.of(0.1d, 0.2d),
            Map.of("kind", "ace")
        );

        Map<String, Object> serialized = node.toDict();

        assertEquals("vec-1", serialized.get("id"));
        assertEquals("memory fragment", serialized.get("content"));
        assertEquals(List.of(0.1d, 0.2d), serialized.get("embedding"));
        assertEquals(Map.of("kind", "ace"), serialized.get("metadata"));

        VectorNode roundTrip = VectorNode.fromDict(serialized);
        assertEquals("vec-1", roundTrip.getId());
        assertEquals("memory fragment", roundTrip.getContent());
        assertEquals(List.of(0.1d, 0.2d), roundTrip.getEmbedding());
        assertEquals(Map.of("kind", "ace"), roundTrip.getMetadata());
    }

    @Test
    void modelRemainsMutableAndPreviewMatchesPythonRepr() {
        VectorNode node = new VectorNode("vec-1", "short");

        node.setId("vec-2");
        node.setContent("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890");
        node.setEmbedding(List.of(1.0d, 2.0d));
        node.getMetadata().put("source", "memory");

        assertEquals("vec-2", node.getId());
        assertEquals(List.of(1.0d, 2.0d), node.getEmbedding());
        assertEquals(Map.of("source", "memory"), node.getMetadata());
        assertTrue(node.toString().startsWith("VectorNode(id=vec-2, content='"));
        assertTrue(node.toString().contains("..."));
    }

    @Test
    void embeddingDefaultsToNullLikeOptionalPythonField() {
        VectorNode node = new VectorNode("vec-1", "short");

        assertNull(node.getEmbedding());
        assertEquals(Map.of(), node.getMetadata());
    }
}
