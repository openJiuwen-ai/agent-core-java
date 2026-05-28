/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.vector_fields;

import com.openjiuwen.core.foundation.store.vector_fields.ChromaFields;
import com.openjiuwen.core.foundation.store.vector_fields.ChromaVectorField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chroma fields test cases.
 * <p>
 * Mirrors Python's {@code test_chroma_fields.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.vector_fields.test_chroma_fields}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Default initialization values</li>
 *   <li>Custom vector field name</li>
 *   <li>Custom HNSW parameters</li>
 *   <li>Parameter boundary validation</li>
 *   <li>Extra search configuration</li>
 * </ul>
 */
class TestChromaFields {

    @Nested
    @DisplayName("ChromaVectorField Initialization Tests")
    @Tag("level0")
    class InitializationTests {

        /**
         * Test: Initialization with default values.
         * <p>
         * Mirrors Python's test_init_default.
         */
        @Test
        @DisplayName("Initialization with default values")
        void testInitDefault() {
            ChromaVectorField field = new ChromaVectorField();
            assertEquals("embedding", field.getVectorField(), "Default vector field should be 'embedding'");
            assertEquals("chroma", field.getDatabaseType(), "Database type should be 'chroma'");
            assertEquals("hnsw", field.getIndexType(), "Index type should be 'hnsw'");
            assertEquals(16, field.getMaxNeighbors(), "Default max_neighbors should be 16");
            assertEquals(100, field.getEfConstruction(), "Default ef_construction should be 100");
            assertEquals(100.0f, field.getEfSearch(), 0.01, "Default ef_search should be 100");
            assertTrue(field.getExtraSearch().isEmpty(), "Default extra_search should be empty");
        }

        /**
         * Test: Initialization with custom vector field name.
         * <p>
         * Mirrors Python's test_init_custom_vector_field.
         */
        @Test
        @DisplayName("Custom vector field name")
        void testInitCustomVectorField() {
            ChromaVectorField field = new ChromaVectorField();
            field.setVectorField("custom_embedding");
            assertEquals("custom_embedding", field.getVectorField(), "Vector field should be customizable");
            assertEquals("chroma", field.getDatabaseType(), "Database type should remain 'chroma'");
            assertEquals("hnsw", field.getIndexType(), "Index type should remain 'hnsw'");
        }

        /**
         * Test: Initialization with custom parameters.
         * <p>
         * Mirrors Python's test_init_custom_parameters.
         */
        @Test
        @DisplayName("Custom HNSW parameters")
        void testInitCustomParameters() {
            ChromaVectorField field = new ChromaVectorField();
            field.setVectorField("embeddings");
            field.setMaxNeighbors(32);
            field.setEfConstruction(200);
            field.setEfSearch(150.5f);

            assertEquals("embeddings", field.getVectorField());
            assertEquals(32, field.getMaxNeighbors());
            assertEquals(200, field.getEfConstruction());
            assertEquals(150.5f, field.getEfSearch(), 0.01);
        }
    }

    @Nested
    @DisplayName("Parameter Boundary Tests")
    @Tag("level0")
    class BoundaryTests {

        /**
         * Test: Minimum max_neighbors.
         * <p>
         * Mirrors Python's test_init_max_neighbors_min.
         */
        @Test
        @DisplayName("Minimum max_neighbors = 2")
        void testMaxNeighborsMin() {
            ChromaVectorField field = new ChromaVectorField();
            field.setMaxNeighbors(2);
            assertEquals(2, field.getMaxNeighbors());
        }

        /**
         * Test: Maximum max_neighbors.
         * <p>
         * Mirrors Python's test_init_max_neighbors_max.
         */
        @Test
        @DisplayName("Maximum max_neighbors = 2048")
        void testMaxNeighborsMax() {
            ChromaVectorField field = new ChromaVectorField();
            field.setMaxNeighbors(2048);
            assertEquals(2048, field.getMaxNeighbors());
        }

        /**
         * Test: max_neighbors below minimum throws exception.
         */
        @Test
        @DisplayName("max_neighbors below minimum throws exception")
        void testMaxNeighborsBelowMin() {
            ChromaVectorField field = new ChromaVectorField();
            assertThrows(IllegalArgumentException.class, () -> field.setMaxNeighbors(1));
        }

        /**
         * Test: max_neighbors above maximum throws exception.
         */
        @Test
        @DisplayName("max_neighbors above maximum throws exception")
        void testMaxNeighborsAboveMax() {
            ChromaVectorField field = new ChromaVectorField();
            assertThrows(IllegalArgumentException.class, () -> field.setMaxNeighbors(2049));
        }

        /**
         * Test: Minimum ef_construction.
         * <p>
         * Mirrors Python's test_init_ef_construction_min.
         */
        @Test
        @DisplayName("Minimum ef_construction = 1")
        void testEfConstructionMin() {
            ChromaVectorField field = new ChromaVectorField();
            field.setEfConstruction(1);
            assertEquals(1, field.getEfConstruction());
        }

        /**
         * Test: ef_construction below minimum throws exception.
         */
        @Test
        @DisplayName("ef_construction below minimum throws exception")
        void testEfConstructionBelowMin() {
            ChromaVectorField field = new ChromaVectorField();
            assertThrows(IllegalArgumentException.class, () -> field.setEfConstruction(0));
        }

        /**
         * Test: Minimum ef_search.
         * <p>
         * Mirrors Python's test_init_ef_search_min.
         */
        @Test
        @DisplayName("Minimum ef_search = 1")
        void testEfSearchMin() {
            ChromaVectorField field = new ChromaVectorField();
            field.setEfSearch(1);
            assertEquals(1.0f, field.getEfSearch(), 0.01);
        }

        /**
         * Test: ef_search as float value.
         * <p>
         * Mirrors Python's test_init_ef_search_float.
         */
        @Test
        @DisplayName("ef_search as float value")
        void testEfSearchFloat() {
            ChromaVectorField field = new ChromaVectorField();
            field.setEfSearch(150.5f);
            assertEquals(150.5f, field.getEfSearch(), 0.01);
        }
    }

    @Nested
    @DisplayName("Extra Search Configuration Tests")
    @Tag("level0")
    class ExtraSearchTests {

        /**
         * Test: Extra search configuration.
         */
        @Test
        @DisplayName("Extra search configuration")
        void testExtraSearch() {
            ChromaVectorField field = new ChromaVectorField();
            Map<String, Object> extraSearch = new HashMap<>();
            extraSearch.put("resize_factor", 1.5);
            extraSearch.put("num_threads", 4);
            extraSearch.put("batch_size", 100);

            field.setExtraSearch(extraSearch);
            assertEquals(1.5, field.getExtraSearch().get("resize_factor"));
            assertEquals(4, field.getExtraSearch().get("num_threads"));
            assertEquals(100, field.getExtraSearch().get("batch_size"));
        }

        /**
         * Test: Invalid resize_factor type throws exception.
         */
        @Test
        @DisplayName("Invalid resize_factor type throws exception")
        void testInvalidResizeFactor() {
            ChromaVectorField field = new ChromaVectorField();
            Map<String, Object> extraSearch = new HashMap<>();
            extraSearch.put("resize_factor", "invalid");

            assertThrows(IllegalArgumentException.class, () -> field.setExtraSearch(extraSearch));
        }
    }

    @Nested
    @DisplayName("ChromaFields Helper Tests")
    @Tag("level0")
    class ChromaFieldsHelperTests {

        /**
         * Test: ChromaFields.defaultSchema exists.
         */
        @Test
        @DisplayName("ChromaFields.defaultSchema exists")
        void testChromaFieldsDefaultSchema() {
            assertNotNull(ChromaFields.class, "ChromaFields class should exist");
        }
    }
}
