/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code TestTriple} in
 * {@code tests/unit_tests/core/retrieval/common/test_triple.py}.
 */
class TripleTest {

    @Test
    void createTripleUsesEmptyMetadataDefault() {
        Triple triple = new Triple("Alice", "knows", "Bob");

        assertThat(triple.getSubject()).isEqualTo("Alice");
        assertThat(triple.getPredicate()).isEqualTo("knows");
        assertThat(triple.getObject()).isEqualTo("Bob");
        assertThat(triple.getMetadata()).isEmpty();
    }

    @Test
    void createTripleWithMetadataStoresMetadata() {
        Map<String, Object> metadata = Map.of("source", "test", "doc_id", "doc_1");

        Triple triple = new Triple("Alice", "knows", "Bob", metadata);

        assertThat(triple.getMetadata()).containsExactlyInAnyOrderEntriesOf(metadata);
    }

    @Test
    void createTripleWithAllFieldsStoresEveryField() {
        Map<String, Object> metadata = Map.of("source", "test");

        Triple triple = new Triple("Alice", "knows", "Bob", metadata);

        assertThat(triple.getSubject()).isEqualTo("Alice");
        assertThat(triple.getPredicate()).isEqualTo("knows");
        assertThat(triple.getObject()).isEqualTo("Bob");
        assertThat(triple.getMetadata()).containsExactlyEntriesOf(metadata);
    }

    @Test
    void missingRequiredFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Triple(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Triple("Alice", null, null));
        assertThrows(IllegalArgumentException.class, () -> new Triple("Alice", "knows", null));
    }
}
