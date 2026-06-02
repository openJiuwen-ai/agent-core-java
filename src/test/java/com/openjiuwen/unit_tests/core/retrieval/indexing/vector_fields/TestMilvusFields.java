/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.vector_fields;

import com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusFLAT;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusHNSW;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusIVF;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusSCANN;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milvus fields test cases.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/retrieval/indexing/vector_fields/test_milvus_fields.py}.</p>
 */
class TestMilvusFields {

    @Nested
    @DisplayName("MilvusFLAT")
    class MilvusFlatTests {

        @Test
        void testInitDefault() {
            MilvusFLAT field = new MilvusFLAT();
            assertEquals("embedding", field.getVectorField());
            assertEquals("milvus", field.getDatabaseType());
            assertEquals("flat", field.getIndexType());
        }

        @Test
        void testInitCustomVectorField() {
            MilvusFLAT field = new MilvusFLAT();
            field.setVectorField("custom_embedding");
            assertEquals("custom_embedding", field.getVectorField());
        }

        @Test
        void testToDictSearch() {
            MilvusFLAT field = new MilvusFLAT();
            field.setVectorField("embeddings");
            assertTrue(field.toDict("search").isEmpty());
        }

        @Test
        void testToDictConstruct() {
            MilvusFLAT field = new MilvusFLAT();
            field.setVectorField("embeddings");
            assertTrue(field.toDict("construct").isEmpty());
        }
    }

    @Nested
    @DisplayName("MilvusAUTO")
    class MilvusAutoTests {

        @Test
        void testInitDefault() {
            MilvusAUTO field = new MilvusAUTO();
            assertEquals("embedding", field.getVectorField());
            assertEquals("milvus", field.getDatabaseType());
            assertEquals("auto", field.getIndexType());
        }

        @Test
        void testInitCustomVectorField() {
            MilvusAUTO field = new MilvusAUTO();
            field.setVectorField("custom_embedding");
            assertEquals("custom_embedding", field.getVectorField());
        }

        @Test
        void testToDictSearch() {
            assertTrue(new MilvusAUTO().toDict("search").isEmpty());
        }

        @Test
        void testToDictConstruct() {
            assertTrue(new MilvusAUTO().toDict("construct").isEmpty());
        }
    }

    @Nested
    @DisplayName("MilvusSCANN")
    class MilvusScannTests {

        @Test
        void testInitDefault() {
            MilvusSCANN field = new MilvusSCANN();
            assertEquals("embedding", field.getVectorField());
            assertEquals("milvus", field.getDatabaseType());
            assertEquals("scann", field.getIndexType());
            assertEquals(128, field.getNlist());
            assertEquals(8, field.getNprobe());
            assertTrue(field.isWithRawData());
            assertNull(field.getReorderK());
        }

        @Test
        void testInitCustomParameters() {
            MilvusSCANN field = new MilvusSCANN();
            field.setVectorField("embeddings");
            field.setNlist(256);
            field.setNprobe(16);
            field.setWithRawData(false);
            field.setReorderK(50);
            assertEquals("embeddings", field.getVectorField());
            assertEquals(256, field.getNlist());
            assertEquals(16, field.getNprobe());
            assertFalse(field.isWithRawData());
            assertEquals(50, field.getReorderK());
        }

        @Test
        void testValidationBoundaries() {
            MilvusSCANN field = new MilvusSCANN();
            field.setNlist(1);
            field.setNprobe(1);
            assertEquals(1, field.getNlist());
            assertEquals(1, field.getNprobe());

            field.setNlist(65536);
            field.setNprobe(65536);
            assertEquals(65536, field.getNlist());
            assertEquals(65536, field.getNprobe());

            field.setReorderK(1);
            assertEquals(1, field.getReorderK());
        }

        @Test
        void testValidationErrors() {
            assertThrows(IllegalArgumentException.class, () -> new MilvusSCANN().setNlist(0));
            assertThrows(IllegalArgumentException.class, () -> new MilvusSCANN().setNlist(65537));
            assertThrows(IllegalArgumentException.class, () -> new MilvusSCANN().setNprobe(0));
            assertThrows(IllegalArgumentException.class, () -> new MilvusSCANN().setReorderK(0));

            MilvusSCANN field = new MilvusSCANN();
            field.setNlist(64);
            assertThrows(IllegalArgumentException.class, () -> field.setNprobe(128));
        }

        @Test
        void testToDictSearch() {
            MilvusSCANN field = new MilvusSCANN();
            field.setNlist(256);
            field.setNprobe(16);
            field.setReorderK(50);
            Map<String, Object> result = field.toDict("search");
            assertEquals(16, result.get("nprobe"));
            assertEquals(50, result.get("reorder_k"));
            assertFalse(result.containsKey("nlist"));
            assertFalse(result.containsKey("with_raw_data"));
        }

        @Test
        void testToDictConstruct() {
            MilvusSCANN field = new MilvusSCANN();
            field.setNlist(256);
            field.setWithRawData(false);
            Map<String, Object> result = field.toDict("construct");
            assertEquals(256, result.get("nlist"));
            assertEquals(false, result.get("with_raw_data"));
            assertFalse(result.containsKey("nprobe"));
            assertFalse(result.containsKey("reorder_k"));
        }

        @Test
        void testToDictSearchWithoutReorderK() {
            MilvusSCANN field = new MilvusSCANN();
            field.setNlist(256);
            field.setNprobe(16);
            Map<String, Object> result = field.toDict("search");
            assertEquals(16, result.get("nprobe"));
            assertFalse(result.containsKey("reorder_k"));
        }
    }

    @Nested
    @DisplayName("MilvusIVF")
    class MilvusIvfTests {

        @Test
        void testInitDefault() {
            MilvusIVF field = new MilvusIVF();
            assertEquals("embedding", field.getVectorField());
            assertEquals("milvus", field.getDatabaseType());
            assertEquals("ivf", field.getIndexType());
            assertEquals("FLAT", field.getVariant());
            assertEquals(128, field.getNlist());
            assertEquals(8, field.getNprobe());
            assertTrue(field.getExtraConstruct().isEmpty());
            assertTrue(field.getExtraSearch().isEmpty());
        }

        @Test
        void testInitCustomParametersAndVariants() {
            MilvusIVF field = new MilvusIVF();
            field.setVectorField("embeddings");
            field.setVariant("SQ8");
            field.setNlist(256);
            field.setNprobe(16);
            assertEquals("embeddings", field.getVectorField());
            assertEquals("SQ8", field.getVariant());
            assertEquals(256, field.getNlist());
            assertEquals(16, field.getNprobe());

            field.setVariant("FLAT");
            assertEquals("FLAT", field.getVariant());
            field.setVariant("PQ");
            assertEquals("PQ", field.getVariant());
            field.setVariant("RABITQ");
            assertEquals("RABITQ", field.getVariant());
        }

        @Test
        void testValidateFlatAndSq8ExtraArgs() {
            MilvusIVF field = new MilvusIVF();
            field.setVariant("FLAT");
            field.setExtraConstruct(Map.of("m", 64));
            assertThrows(IllegalArgumentException.class, field::validate);

            field = new MilvusIVF();
            field.setVariant("SQ8");
            field.setExtraSearch(Map.of("refine_k", 1.5));
            assertThrows(IllegalArgumentException.class, field::validate);
        }

        @Test
        void testValidatePqVariant() {
            MilvusIVF field = new MilvusIVF();
            field.setVariant("PQ");
            field.setExtraConstruct(Map.of("m", 64, "nbits", 8));
            field.validate();

            MilvusIVF invalidM = new MilvusIVF();
            invalidM.setVariant("PQ");
            invalidM.setExtraConstruct(Map.of("m", 0));
            assertThrows(IllegalArgumentException.class, invalidM::validate);

            MilvusIVF invalidNbits = new MilvusIVF();
            invalidNbits.setVariant("PQ");
            invalidNbits.setExtraConstruct(Map.of("m", 64, "nbits", 25));
            assertThrows(IllegalArgumentException.class, invalidNbits::validate);
        }

        @Test
        void testValidateRabitqVariant() {
            MilvusIVF field = new MilvusIVF();
            field.setVariant("RABITQ");
            field.setExtraConstruct(Map.of("refine", true, "refine_type", "SQ8"));
            field.setExtraSearch(Map.of("refine_k", 1.5, "rbq_query_bits", 4));
            field.validate();

            MilvusIVF invalidRefineType = new MilvusIVF();
            invalidRefineType.setVariant("RABITQ");
            invalidRefineType.setExtraConstruct(Map.of("refine", true, "refine_type", "INVALID"));
            assertThrows(IllegalArgumentException.class, invalidRefineType::validate);

            MilvusIVF invalidRefineK = new MilvusIVF();
            invalidRefineK.setVariant("RABITQ");
            invalidRefineK.setExtraSearch(Map.of("refine_k", 0.5));
            assertThrows(IllegalArgumentException.class, invalidRefineK::validate);

            MilvusIVF invalidBits = new MilvusIVF();
            invalidBits.setVariant("RABITQ");
            invalidBits.setExtraSearch(Map.of("rbq_query_bits", 9));
            assertThrows(IllegalArgumentException.class, invalidBits::validate);
        }

        @Test
        void testValidationNprobeGreaterThanNlist() {
            MilvusIVF field = new MilvusIVF();
            field.setNlist(64);
            assertThrows(IllegalArgumentException.class, () -> field.setNprobe(128));
        }

        @Test
        void testToDictSearchAndConstruct() {
            MilvusIVF field = new MilvusIVF();
            field.setVariant("FLAT");
            field.setNlist(256);
            field.setNprobe(16);
            Map<String, Object> search = field.toDict("search");
            assertEquals(16, search.get("nprobe"));
            assertFalse(search.containsKey("nlist"));
            assertFalse(search.containsKey("variant"));

            Map<String, Object> construct = field.toDict("construct");
            assertEquals(256, construct.get("nlist"));
            assertFalse(construct.containsKey("nprobe"));
            assertFalse(construct.containsKey("variant"));
        }

        @Test
        void testToDictSearchWithExtraSearch() {
            MilvusIVF field = new MilvusIVF();
            field.setVariant("RABITQ");
            field.setNlist(256);
            field.setNprobe(16);
            field.setExtraSearch(Map.of("refine_k", 1.5, "rbq_query_bits", 4));
            Map<String, Object> result = field.toDict("search");
            assertEquals(16, result.get("nprobe"));
            assertEquals(1.5, result.get("refine_k"));
            assertEquals(4, result.get("rbq_query_bits"));
            assertFalse(result.containsKey("extra_search"));
        }

        @Test
        void testToDictConstructWithExtraConstruct() {
            MilvusIVF field = new MilvusIVF();
            field.setVariant("PQ");
            field.setNlist(256);
            field.setNprobe(16);
            field.setExtraConstruct(Map.of("m", 64, "nbits", 8));
            Map<String, Object> result = field.toDict("construct");
            assertEquals(256, result.get("nlist"));
            assertEquals(64, result.get("m"));
            assertEquals(8, result.get("nbits"));
            assertFalse(result.containsKey("extra_construct"));
        }
    }

    @Nested
    @DisplayName("MilvusHNSW")
    class MilvusHnswTests {

        @Test
        void testInitDefault() {
            MilvusHNSW field = new MilvusHNSW();
            assertEquals("embedding", field.getVectorField());
            assertEquals("milvus", field.getDatabaseType());
            assertEquals("hnsw", field.getIndexType());
            assertEquals(30, field.getM());
            assertEquals(360, field.getEfConstruction());
            assertNull(field.getEfSearchFactor());
            assertNull(field.getVariant());
            assertTrue(field.getExtraConstruct().isEmpty());
            assertTrue(field.getExtraSearch().isEmpty());
        }

        @Test
        void testInitCustomParameters() {
            MilvusHNSW field = new MilvusHNSW();
            field.setVectorField("embeddings");
            field.setM(64);
            field.setEfConstruction(400);
            field.setEfSearchFactor(2.0f);
            assertEquals("embeddings", field.getVectorField());
            assertEquals(64, field.getM());
            assertEquals(400, field.getEfConstruction());
            assertEquals(2.0f, field.getEfSearchFactor());
        }

        @Test
        void testBoundaryValidation() {
            MilvusHNSW field = new MilvusHNSW();
            field.setM(2);
            field.setM(2048);
            field.setEfConstruction(1);
            field.setEfSearchFactor(1.0f);
            assertThrows(IllegalArgumentException.class, () -> new MilvusHNSW().setM(1));
            assertThrows(IllegalArgumentException.class, () -> new MilvusHNSW().setM(2049));
            assertThrows(IllegalArgumentException.class, () -> new MilvusHNSW().setEfConstruction(0));
            assertThrows(IllegalArgumentException.class, () -> new MilvusHNSW().setEfSearchFactor(0.5f));
        }

        @Test
        void testValidateSqVariant() {
            MilvusHNSW field = new MilvusHNSW();
            field.setVariant("SQ");
            field.setExtraConstruct(Map.of("sq_type", "SQ8", "refine", true, "refine_type", "FP16"));
            field.validate();

            MilvusHNSW invalidSqType = new MilvusHNSW();
            invalidSqType.setVariant("SQ");
            invalidSqType.setExtraConstruct(Map.of("sq_type", "INVALID"));
            assertThrows(IllegalArgumentException.class, invalidSqType::validate);

            MilvusHNSW invalidRefineType = new MilvusHNSW();
            invalidRefineType.setVariant("SQ");
            invalidRefineType.setExtraConstruct(Map.of("refine", true, "refine_type", "INVALID"));
            assertThrows(IllegalArgumentException.class, invalidRefineType::validate);
        }

        @Test
        void testValidatePqVariant() {
            MilvusHNSW field = new MilvusHNSW();
            field.setVariant("PQ");
            field.setExtraConstruct(Map.of("m", 64, "nbits", 8, "refine", true, "refine_type", "FP16"));
            field.setExtraSearch(Map.of("refine_k", 1.5));
            field.validate();

            MilvusHNSW invalidM = new MilvusHNSW();
            invalidM.setVariant("PQ");
            invalidM.setExtraConstruct(Map.of("m", 0));
            assertThrows(IllegalArgumentException.class, invalidM::validate);

            MilvusHNSW invalidNbits = new MilvusHNSW();
            invalidNbits.setVariant("PQ");
            invalidNbits.setExtraConstruct(Map.of("m", 64, "nbits", 0));
            assertThrows(IllegalArgumentException.class, invalidNbits::validate);

            MilvusHNSW invalidRefineK = new MilvusHNSW();
            invalidRefineK.setVariant("PQ");
            invalidRefineK.setExtraSearch(Map.of("refine_k", 0.5));
            assertThrows(IllegalArgumentException.class, invalidRefineK::validate);
        }

        @Test
        void testValidatePrqVariant() {
            MilvusHNSW field = new MilvusHNSW();
            field.setVariant("PRQ");
            field.setExtraConstruct(Map.of("m", 64, "nbits", 8, "nrq", 4, "refine", true, "refine_type", "FP16"));
            field.setExtraSearch(Map.of("refine_k", 1.5));
            field.validate();

            MilvusHNSW invalidNrq = new MilvusHNSW();
            invalidNrq.setVariant("PRQ");
            invalidNrq.setExtraConstruct(Map.of("nrq", 0));
            assertThrows(IllegalArgumentException.class, invalidNrq::validate);

            MilvusHNSW invalidNrqHigh = new MilvusHNSW();
            invalidNrqHigh.setVariant("PRQ");
            invalidNrqHigh.setExtraConstruct(Map.of("nrq", 17));
            assertThrows(IllegalArgumentException.class, invalidNrqHigh::validate);
        }

        @Test
        void testToDictSearchAndConstruct() {
            MilvusHNSW field = new MilvusHNSW();
            field.setM(64);
            field.setEfConstruction(400);
            field.setEfSearchFactor(2.0f);

            Map<String, Object> search = field.toDict("search");
            assertEquals(2.0f, search.get("efSearchFactor"));
            assertFalse(search.containsKey("M"));
            assertFalse(search.containsKey("efConstruction"));

            Map<String, Object> construct = field.toDict("construct");
            assertEquals(64, construct.get("M"));
            assertEquals(400, construct.get("efConstruction"));
            assertFalse(construct.containsKey("efSearchFactor"));
        }

        @Test
        void testToDictWithExtraArgumentsMerged() {
            MilvusHNSW searchField = new MilvusHNSW();
            searchField.setVariant("PQ");
            searchField.setEfSearchFactor(2.0f);
            searchField.setExtraSearch(Map.of("refine_k", 1.5));
            Map<String, Object> search = searchField.toDict("search");
            assertEquals(2.0f, search.get("efSearchFactor"));
            assertEquals(1.5, search.get("refine_k"));
            assertFalse(search.containsKey("extra_search"));

            MilvusHNSW constructField = new MilvusHNSW();
            constructField.setVariant("SQ");
            constructField.setM(64);
            constructField.setEfConstruction(400);
            constructField.setExtraConstruct(Map.of("sq_type", "SQ8", "refine", true));
            Map<String, Object> construct = constructField.toDict("construct");
            assertEquals(64, construct.get("M"));
            assertEquals(400, construct.get("efConstruction"));
            assertEquals("SQ8", construct.get("sq_type"));
            assertEquals(true, construct.get("refine"));
            assertFalse(construct.containsKey("extra_construct"));
        }

        @Test
        void testSqVariantAcceptsKnownTypes() {
            for (String sqType : new String[]{"SQ4U", "SQ6", "SQ8", "FP16", "BF16"}) {
                MilvusHNSW field = new MilvusHNSW();
                field.setVariant("SQ");
                field.setExtraConstruct(Map.of("sq_type", sqType));
                field.validate();
            }

            for (String refineType : new String[]{"SQ6", "SQ8", "FP16", "BF16", "FP32"}) {
                MilvusHNSW field = new MilvusHNSW();
                field.setVariant("SQ");
                field.setExtraConstruct(Map.of("refine", true, "refine_type", refineType));
                field.validate();
            }
        }
    }
}
