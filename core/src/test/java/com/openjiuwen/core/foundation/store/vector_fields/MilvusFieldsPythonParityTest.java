/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Supplemental parity tests for Milvus vector-field configuration models.
 *
 * <p>Mirrors Python's supplemental test module in
 * {@code tests/unit_tests/core/retrieval/indexing/vector_fields/test_milvus_fields.py}.</p>
 */
class MilvusFieldsPythonParityTest {

    @TestFactory
    List<DynamicTest> pythonMilvusFieldParityCases() {
        return List.of(
                parity("TestMilvusFLAT::test_init_default", this::caseFlatInitDefault),
                parity("TestMilvusFLAT::test_init_custom_vector_field", this::caseFlatInitCustomVectorField),
                parity("TestMilvusFLAT::test_to_dict_search", this::caseFlatToDictSearch),
                parity("TestMilvusFLAT::test_to_dict_construct", this::caseFlatToDictConstruct),
                parity("TestMilvusAUTO::test_init_default", this::caseAutoInitDefault),
                parity("TestMilvusAUTO::test_init_custom_vector_field", this::caseAutoInitCustomVectorField),
                parity("TestMilvusAUTO::test_to_dict_search", this::caseAutoToDictSearch),
                parity("TestMilvusAUTO::test_to_dict_construct", this::caseAutoToDictConstruct),
                parity("TestMilvusSCANN::test_init_default", this::caseScannInitDefault),
                parity("TestMilvusSCANN::test_init_custom_parameters", this::caseScannInitCustomParameters),
                parity("TestMilvusSCANN::test_init_nlist_min", this::caseScannInitNlistMin),
                parity("TestMilvusSCANN::test_init_nlist_max", this::caseScannInitNlistMax),
                parity("TestMilvusSCANN::test_init_nprobe_min", this::caseScannInitNprobeMin),
                parity("TestMilvusSCANN::test_init_nprobe_max", this::caseScannInitNprobeMax),
                parity("TestMilvusSCANN::test_init_reorder_k_min", this::caseScannInitReorderKMin),
                parity("TestMilvusSCANN::test_validation_nlist_too_low", this::caseScannValidationNlistTooLow),
                parity("TestMilvusSCANN::test_validation_nlist_too_high", this::caseScannValidationNlistTooHigh),
                parity("TestMilvusSCANN::test_validation_nprobe_too_low", this::caseScannValidationNprobeTooLow),
                parity("TestMilvusSCANN::test_validation_nprobe_too_high", this::caseScannValidationNprobeTooHigh),
                parity("TestMilvusSCANN::test_validation_nprobe_greater_than_nlist",
                        this::caseScannValidationNprobeGreaterThanNlist),
                parity("TestMilvusSCANN::test_validation_reorder_k_too_low",
                        this::caseScannValidationReorderKTooLow),
                parity("TestMilvusSCANN::test_to_dict_search", this::caseScannToDictSearch),
                parity("TestMilvusSCANN::test_to_dict_construct", this::caseScannToDictConstruct),
                parity("TestMilvusSCANN::test_to_dict_search_without_reorder_k",
                        this::caseScannToDictSearchWithoutReorderK),
                parity("TestMilvusIVF::test_init_default", this::caseIvfInitDefault),
                parity("TestMilvusIVF::test_init_custom_parameters", this::caseIvfInitCustomParameters),
                parity("TestMilvusIVF::test_init_variant_flat", this::caseIvfInitVariantFlat),
                parity("TestMilvusIVF::test_init_variant_sq8", this::caseIvfInitVariantSq8),
                parity("TestMilvusIVF::test_init_variant_pq", this::caseIvfInitVariantPq),
                parity("TestMilvusIVF::test_init_variant_rabitq", this::caseIvfInitVariantRabitq),
                parity("TestMilvusIVF::test_validation_nprobe_greater_than_nlist",
                        this::caseIvfValidationNprobeGreaterThanNlist),
                parity("TestMilvusIVF::test_validation_flat_with_extra_args",
                        this::caseIvfValidationFlatWithExtraArgs),
                parity("TestMilvusIVF::test_validation_sq8_with_extra_args",
                        this::caseIvfValidationSq8WithExtraArgs),
                parity("TestMilvusIVF::test_validation_pq_with_extra_search",
                        this::caseIvfValidationPqWithExtraSearch),
                parity("TestMilvusIVF::test_validation_pq_invalid_m", this::caseIvfValidationPqInvalidM),
                parity("TestMilvusIVF::test_validation_pq_invalid_nbits", this::caseIvfValidationPqInvalidNbits),
                parity("TestMilvusIVF::test_validation_pq_nbits_too_high",
                        this::caseIvfValidationPqNbitsTooHigh),
                parity("TestMilvusIVF::test_validation_rabitq_invalid_refine_type",
                        this::caseIvfValidationRabitqInvalidRefineType),
                parity("TestMilvusIVF::test_validation_rabitq_invalid_refine_k",
                        this::caseIvfValidationRabitqInvalidRefineK),
                parity("TestMilvusIVF::test_validation_rabitq_invalid_rbq_query_bits",
                        this::caseIvfValidationRabitqInvalidRbqQueryBits),
                parity("TestMilvusIVF::test_to_dict_search", this::caseIvfToDictSearch),
                parity("TestMilvusIVF::test_to_dict_construct", this::caseIvfToDictConstruct),
                parity("TestMilvusIVF::test_to_dict_search_with_extra_search",
                        this::caseIvfToDictSearchWithExtraSearch),
                parity("TestMilvusIVF::test_to_dict_construct_with_extra_construct",
                        this::caseIvfToDictConstructWithExtraConstruct),
                parity("TestMilvusHNSW::test_init_default", this::caseHnswInitDefault),
                parity("TestMilvusHNSW::test_init_custom_parameters", this::caseHnswInitCustomParameters),
                parity("TestMilvusHNSW::test_init_m_min", this::caseHnswInitMMin),
                parity("TestMilvusHNSW::test_init_m_max", this::caseHnswInitMMax),
                parity("TestMilvusHNSW::test_init_ef_construction_min", this::caseHnswInitEfConstructionMin),
                parity("TestMilvusHNSW::test_init_ef_search_factor_min",
                        this::caseHnswInitEfSearchFactorMin),
                parity("TestMilvusHNSW::test_init_variant_sq", this::caseHnswInitVariantSq),
                parity("TestMilvusHNSW::test_init_variant_pq", this::caseHnswInitVariantPq),
                parity("TestMilvusHNSW::test_init_variant_prq", this::caseHnswInitVariantPrq),
                parity("TestMilvusHNSW::test_validation_m_too_low", this::caseHnswValidationMTooLow),
                parity("TestMilvusHNSW::test_validation_m_too_high", this::caseHnswValidationMTooHigh),
                parity("TestMilvusHNSW::test_validation_ef_construction_too_low",
                        this::caseHnswValidationEfConstructionTooLow),
                parity("TestMilvusHNSW::test_validation_ef_search_factor_too_low",
                        this::caseHnswValidationEfSearchFactorTooLow),
                parity("TestMilvusHNSW::test_validation_sq_invalid_sq_type",
                        this::caseHnswValidationSqInvalidSqType),
                parity("TestMilvusHNSW::test_validation_sq_invalid_refine_type",
                        this::caseHnswValidationSqInvalidRefineType),
                parity("TestMilvusHNSW::test_validation_pq_invalid_m", this::caseHnswValidationPqInvalidM),
                parity("TestMilvusHNSW::test_validation_pq_invalid_nbits", this::caseHnswValidationPqInvalidNbits),
                parity("TestMilvusHNSW::test_validation_pq_invalid_refine_k",
                        this::caseHnswValidationPqInvalidRefineK),
                parity("TestMilvusHNSW::test_validation_prq_invalid_nrq", this::caseHnswValidationPrqInvalidNrq),
                parity("TestMilvusHNSW::test_validation_prq_nrq_too_high",
                        this::caseHnswValidationPrqNrqTooHigh),
                parity("TestMilvusHNSW::test_to_dict_search", this::caseHnswToDictSearch),
                parity("TestMilvusHNSW::test_to_dict_construct", this::caseHnswToDictConstruct),
                parity("TestMilvusHNSW::test_to_dict_search_without_ef_search_factor",
                        this::caseHnswToDictSearchWithoutEfSearchFactor),
                parity("TestMilvusHNSW::test_to_dict_search_with_extra_search",
                        this::caseHnswToDictSearchWithExtraSearch),
                parity("TestMilvusHNSW::test_to_dict_construct_with_extra_construct",
                        this::caseHnswToDictConstructWithExtraConstruct),
                parity("TestMilvusHNSW::test_sq_variant_sq_types", this::caseHnswSqVariantSqTypes),
                parity("TestMilvusHNSW::test_sq_variant_refine_types", this::caseHnswSqVariantRefineTypes)
        );
    }

    private void caseFlatInitDefault() {
        MilvusFLAT field = new MilvusFLAT();
        assertCommon(field, "embedding", "milvus", "flat");
    }

    private void caseFlatInitCustomVectorField() {
        MilvusFLAT field = new MilvusFLAT();
        field.setVectorField("custom_embedding");
        assertCommon(field, "custom_embedding", "milvus", "flat");
    }

    private void caseFlatToDictSearch() {
        MilvusFLAT field = new MilvusFLAT();
        field.setVectorField("embeddings");
        assertEmptyNoInternal(field.toDict(VectorField.STAGE_SEARCH));
    }

    private void caseFlatToDictConstruct() {
        MilvusFLAT field = new MilvusFLAT();
        field.setVectorField("embeddings");
        assertEmptyNoInternal(field.toDict(VectorField.STAGE_CONSTRUCT));
    }

    private void caseAutoInitDefault() {
        MilvusAUTO field = new MilvusAUTO();
        assertCommon(field, "embedding", "milvus", "auto");
    }

    private void caseAutoInitCustomVectorField() {
        MilvusAUTO field = new MilvusAUTO();
        field.setVectorField("custom_embedding");
        assertCommon(field, "custom_embedding", "milvus", "auto");
    }

    private void caseAutoToDictSearch() {
        MilvusAUTO field = new MilvusAUTO();
        field.setVectorField("embeddings");
        assertEmptyNoInternal(field.toDict(VectorField.STAGE_SEARCH));
    }

    private void caseAutoToDictConstruct() {
        MilvusAUTO field = new MilvusAUTO();
        field.setVectorField("embeddings");
        assertEmptyNoInternal(field.toDict(VectorField.STAGE_CONSTRUCT));
    }

    private void caseScannInitDefault() {
        MilvusSCANN field = new MilvusSCANN();
        assertCommon(field, "embedding", "milvus", "scann");
        assertThat(field.getNlist()).isEqualTo(128);
        assertThat(field.getNprobe()).isEqualTo(8);
        assertThat(field.isWithRawData()).isTrue();
        assertThat(field.getReorderK()).isNull();
    }

    private void caseScannInitCustomParameters() {
        MilvusSCANN field = scann(item -> {
            item.setVectorField("embeddings");
            item.setNlist(256);
            item.setNprobe(16);
            item.setWithRawData(false);
            item.setReorderK(50);
        });
        assertThat(field.getVectorField()).isEqualTo("embeddings");
        assertThat(field.getNlist()).isEqualTo(256);
        assertThat(field.getNprobe()).isEqualTo(16);
        assertThat(field.isWithRawData()).isFalse();
        assertThat(field.getReorderK()).isEqualTo(50);
    }

    private void caseScannInitNlistMin() {
        MilvusSCANN field = scann(item -> {
            item.setNlist(1);
            item.setNprobe(1);
        });
        assertThat(field.getNlist()).isEqualTo(1);
        assertThat(field.getNprobe()).isEqualTo(1);
    }

    private void caseScannInitNlistMax() {
        assertThat(scann(item -> item.setNlist(65536)).getNlist()).isEqualTo(65536);
    }

    private void caseScannInitNprobeMin() {
        assertThat(scann(item -> item.setNprobe(1)).getNprobe()).isEqualTo(1);
    }

    private void caseScannInitNprobeMax() {
        MilvusSCANN field = scann(item -> {
            item.setNlist(65536);
            item.setNprobe(65536);
        });
        assertThat(field.getNlist()).isEqualTo(65536);
        assertThat(field.getNprobe()).isEqualTo(65536);
    }

    private void caseScannInitReorderKMin() {
        assertThat(scann(item -> item.setReorderK(1)).getReorderK()).isEqualTo(1);
    }

    private void caseScannValidationNlistTooLow() {
        assertValidation(() -> scann(item -> item.setNlist(0)), "nlist");
    }

    private void caseScannValidationNlistTooHigh() {
        assertValidation(() -> scann(item -> item.setNlist(65537)), "nlist");
    }

    private void caseScannValidationNprobeTooLow() {
        assertValidation(() -> scann(item -> item.setNprobe(0)), "nprobe");
    }

    private void caseScannValidationNprobeTooHigh() {
        assertValidation(() -> scann(item -> item.setNprobe(65537)), "nprobe");
    }

    private void caseScannValidationNprobeGreaterThanNlist() {
        assertValidation(() -> scann(item -> {
            item.setNlist(64);
            item.setNprobe(128);
            item.toDict(VectorField.STAGE_SEARCH);
        }), "nprobe");
    }

    private void caseScannValidationReorderKTooLow() {
        assertValidation(() -> scann(item -> item.setReorderK(0)), "reorder");
    }

    private void caseScannToDictSearch() {
        MilvusSCANN field = scann(item -> {
            item.setNlist(256);
            item.setNprobe(16);
            item.setReorderK(50);
        });
        Map<String, Object> result = field.toDict(VectorField.STAGE_SEARCH);
        assertThat(result).containsEntry("nprobe", 16).containsEntry("reorder_k", 50);
        assertThat(result).doesNotContainKeys("nlist", "with_raw_data", "database_type", "index_type", "vector_field");
    }

    private void caseScannToDictConstruct() {
        MilvusSCANN field = scann(item -> {
            item.setNlist(256);
            item.setNprobe(16);
            item.setWithRawData(false);
        });
        Map<String, Object> result = field.toDict(VectorField.STAGE_CONSTRUCT);
        assertThat(result).containsEntry("nlist", 256).containsEntry("with_raw_data", false);
        assertThat(result).doesNotContainKeys("nprobe", "reorder_k", "database_type", "index_type", "vector_field");
    }

    private void caseScannToDictSearchWithoutReorderK() {
        MilvusSCANN field = scann(item -> {
            item.setNlist(256);
            item.setNprobe(16);
        });
        assertThat(field.toDict(VectorField.STAGE_SEARCH)).containsKey("nprobe").doesNotContainKey("reorder_k");
    }

    private void caseIvfInitDefault() {
        MilvusIVF field = new MilvusIVF();
        assertCommon(field, "embedding", "milvus", "ivf");
        assertThat(field.getVariant()).isEqualTo("FLAT");
        assertThat(field.getNlist()).isEqualTo(128);
        assertThat(field.getNprobe()).isEqualTo(8);
        assertThat(field.getExtraConstruct()).isEmpty();
        assertThat(field.getExtraSearch()).isEmpty();
    }

    private void caseIvfInitCustomParameters() {
        MilvusIVF field = ivf(item -> {
            item.setVectorField("embeddings");
            item.setVariant("SQ8");
            item.setNlist(256);
            item.setNprobe(16);
        });
        assertThat(field.getVectorField()).isEqualTo("embeddings");
        assertThat(field.getVariant()).isEqualTo("SQ8");
        assertThat(field.getNlist()).isEqualTo(256);
        assertThat(field.getNprobe()).isEqualTo(16);
    }

    private void caseIvfInitVariantFlat() {
        assertThat(ivf(item -> item.setVariant("FLAT")).getVariant()).isEqualTo("FLAT");
    }

    private void caseIvfInitVariantSq8() {
        assertThat(ivf(item -> item.setVariant("SQ8")).getVariant()).isEqualTo("SQ8");
    }

    private void caseIvfInitVariantPq() {
        MilvusIVF field = ivf(item -> {
            item.setVariant("PQ");
            item.setExtraConstruct(mapOf("m", 64, "nbits", 8));
        });
        assertThat(field.getVariant()).isEqualTo("PQ");
        assertThat(field.getExtraConstruct()).containsEntry("m", 64).containsEntry("nbits", 8);
    }

    private void caseIvfInitVariantRabitq() {
        MilvusIVF field = ivf(item -> {
            item.setVariant("RABITQ");
            item.setExtraConstruct(mapOf("refine", true, "refine_type", "SQ8"));
            item.setExtraSearch(mapOf("refine_k", 1.5d, "rbq_query_bits", 4));
        });
        assertThat(field.getVariant()).isEqualTo("RABITQ");
        assertThat(field.getExtraConstruct()).containsEntry("refine", true).containsEntry("refine_type", "SQ8");
        assertThat(field.getExtraSearch()).containsEntry("refine_k", 1.5d).containsEntry("rbq_query_bits", 4);
    }

    private void caseIvfValidationNprobeGreaterThanNlist() {
        assertValidation(() -> ivf(item -> {
            item.setNlist(64);
            item.setNprobe(128);
            item.toDict(VectorField.STAGE_SEARCH);
        }), "nprobe");
    }

    private void caseIvfValidationFlatWithExtraArgs() {
        assertValidation(() -> ivf(item -> {
            item.setVariant("FLAT");
            item.setExtraConstruct(Map.of("m", 64));
            item.toDict(VectorField.STAGE_CONSTRUCT);
        }), "invalid extra");
    }

    private void caseIvfValidationSq8WithExtraArgs() {
        assertValidation(() -> ivf(item -> {
            item.setVariant("SQ8");
            item.setExtraSearch(Map.of("refine_k", 1.5d));
            item.toDict(VectorField.STAGE_SEARCH);
        }), "invalid extra");
    }

    private void caseIvfValidationPqWithExtraSearch() {
        assertValidation(() -> ivf(item -> {
            item.setVariant("PQ");
            item.setExtraConstruct(mapOf("m", 64, "nbits", 8));
            item.setExtraSearch(Map.of("refine_k", 1.5d));
            item.toDict(VectorField.STAGE_SEARCH);
        }), "extra search");
    }

    private void caseIvfValidationPqInvalidM() {
        assertValidation(() -> ivf(item -> {
            item.setVariant("PQ");
            item.setExtraConstruct(Map.of("m", 0));
            item.toDict(VectorField.STAGE_CONSTRUCT);
        }), "m");
    }

    private void caseIvfValidationPqInvalidNbits() {
        assertValidation(() -> ivf(item -> {
            item.setVariant("PQ");
            item.setExtraConstruct(mapOf("m", 64, "nbits", 0));
            item.toDict(VectorField.STAGE_CONSTRUCT);
        }), "nbits");
    }

    private void caseIvfValidationPqNbitsTooHigh() {
        assertValidation(() -> ivf(item -> {
            item.setVariant("PQ");
            item.setExtraConstruct(mapOf("m", 64, "nbits", 25));
            item.toDict(VectorField.STAGE_CONSTRUCT);
        }), "nbits");
    }

    private void caseIvfValidationRabitqInvalidRefineType() {
        assertValidation(() -> ivf(item -> {
            item.setVariant("RABITQ");
            item.setExtraConstruct(mapOf("refine", true, "refine_type", "INVALID"));
            item.toDict(VectorField.STAGE_CONSTRUCT);
        }), "refine_type");
    }

    private void caseIvfValidationRabitqInvalidRefineK() {
        assertValidation(() -> ivf(item -> {
            item.setVariant("RABITQ");
            item.setExtraSearch(Map.of("refine_k", 0.5d));
            item.toDict(VectorField.STAGE_SEARCH);
        }), "refine_k");
    }

    private void caseIvfValidationRabitqInvalidRbqQueryBits() {
        assertValidation(() -> ivf(item -> {
            item.setVariant("RABITQ");
            item.setExtraSearch(Map.of("rbq_query_bits", 9));
            item.toDict(VectorField.STAGE_SEARCH);
        }), "rbq_query_bits");
    }

    private void caseIvfToDictSearch() {
        MilvusIVF field = ivf(item -> {
            item.setNlist(256);
            item.setNprobe(16);
            item.setVariant("FLAT");
        });
        assertThat(field.toDict(VectorField.STAGE_SEARCH))
                .containsEntry("nprobe", 16)
                .doesNotContainKeys("nlist", "database_type", "index_type", "vector_field", "variant");
    }

    private void caseIvfToDictConstruct() {
        MilvusIVF field = ivf(item -> {
            item.setNlist(256);
            item.setNprobe(16);
            item.setVariant("FLAT");
        });
        assertThat(field.toDict(VectorField.STAGE_CONSTRUCT))
                .containsEntry("nlist", 256)
                .doesNotContainKeys("nprobe", "database_type", "index_type", "vector_field", "variant");
    }

    private void caseIvfToDictSearchWithExtraSearch() {
        MilvusIVF field = ivf(item -> {
            item.setVariant("RABITQ");
            item.setNlist(256);
            item.setNprobe(16);
            item.setExtraSearch(mapOf("refine_k", 1.5d, "rbq_query_bits", 4));
        });
        assertThat(field.toDict(VectorField.STAGE_SEARCH))
                .containsEntry("nprobe", 16)
                .containsEntry("refine_k", 1.5d)
                .containsEntry("rbq_query_bits", 4)
                .doesNotContainKey("extra_search");
    }

    private void caseIvfToDictConstructWithExtraConstruct() {
        MilvusIVF field = ivf(item -> {
            item.setVariant("PQ");
            item.setNlist(256);
            item.setNprobe(16);
            item.setExtraConstruct(mapOf("m", 64, "nbits", 8));
        });
        assertThat(field.toDict(VectorField.STAGE_CONSTRUCT))
                .containsEntry("nlist", 256)
                .containsEntry("m", 64)
                .containsEntry("nbits", 8)
                .doesNotContainKey("extra_construct");
    }

    private void caseHnswInitDefault() {
        MilvusHNSW field = new MilvusHNSW();
        assertCommon(field, "embedding", "milvus", "hnsw");
        assertThat(field.getM()).isEqualTo(30);
        assertThat(field.getEfConstruction()).isEqualTo(360);
        assertThat(field.getEfSearchFactor()).isNull();
        assertThat(field.getVariant()).isNull();
        assertThat(field.getExtraConstruct()).isEmpty();
        assertThat(field.getExtraSearch()).isEmpty();
    }

    private void caseHnswInitCustomParameters() {
        MilvusHNSW field = hnsw(item -> {
            item.setVectorField("embeddings");
            item.setM(64);
            item.setEfConstruction(400);
            item.setEfSearchFactor(2.0d);
        });
        assertThat(field.getVectorField()).isEqualTo("embeddings");
        assertThat(field.getM()).isEqualTo(64);
        assertThat(field.getEfConstruction()).isEqualTo(400);
        assertThat(field.getEfSearchFactor()).isEqualTo(2.0d);
    }

    private void caseHnswInitMMin() {
        assertThat(hnsw(item -> item.setM(2)).getM()).isEqualTo(2);
    }

    private void caseHnswInitMMax() {
        assertThat(hnsw(item -> item.setM(2048)).getM()).isEqualTo(2048);
    }

    private void caseHnswInitEfConstructionMin() {
        assertThat(hnsw(item -> item.setEfConstruction(1)).getEfConstruction()).isEqualTo(1);
    }

    private void caseHnswInitEfSearchFactorMin() {
        assertThat(hnsw(item -> item.setEfSearchFactor(1.0d)).getEfSearchFactor()).isEqualTo(1.0d);
    }

    private void caseHnswInitVariantSq() {
        MilvusHNSW field = hnsw(item -> {
            item.setVariant("SQ");
            item.setExtraConstruct(mapOf("sq_type", "SQ8", "refine", true, "refine_type", "FP16"));
        });
        assertThat(field.getVariant()).isEqualTo("SQ");
        assertThat(field.getExtraConstruct())
                .containsEntry("sq_type", "SQ8")
                .containsEntry("refine", true)
                .containsEntry("refine_type", "FP16");
    }

    private void caseHnswInitVariantPq() {
        MilvusHNSW field = hnsw(item -> {
            item.setVariant("PQ");
            item.setExtraConstruct(mapOf("m", 64, "nbits", 8, "refine", true, "refine_type", "FP16"));
            item.setExtraSearch(Map.of("refine_k", 1.5d));
        });
        assertThat(field.getVariant()).isEqualTo("PQ");
        assertThat(field.getExtraConstruct()).containsEntry("m", 64).containsEntry("nbits", 8);
        assertThat(field.getExtraSearch()).containsEntry("refine_k", 1.5d);
    }

    private void caseHnswInitVariantPrq() {
        MilvusHNSW field = hnsw(item -> {
            item.setVariant("PRQ");
            item.setExtraConstruct(mapOf("m", 64, "nbits", 8, "nrq", 4, "refine", true, "refine_type", "FP16"));
            item.setExtraSearch(Map.of("refine_k", 1.5d));
        });
        assertThat(field.getVariant()).isEqualTo("PRQ");
        assertThat(field.getExtraConstruct()).containsEntry("m", 64).containsEntry("nbits", 8).containsEntry("nrq", 4);
        assertThat(field.getExtraSearch()).containsEntry("refine_k", 1.5d);
    }

    private void caseHnswValidationMTooLow() {
        assertValidation(() -> hnsw(item -> item.setM(1)), "M");
    }

    private void caseHnswValidationMTooHigh() {
        assertValidation(() -> hnsw(item -> item.setM(2049)), "M");
    }

    private void caseHnswValidationEfConstructionTooLow() {
        assertValidation(() -> hnsw(item -> item.setEfConstruction(0)), "efConstruction");
    }

    private void caseHnswValidationEfSearchFactorTooLow() {
        assertValidation(() -> hnsw(item -> item.setEfSearchFactor(0.5d)), "efSearchFactor");
    }

    private void caseHnswValidationSqInvalidSqType() {
        assertValidation(() -> hnsw(item -> {
            item.setVariant("SQ");
            item.setExtraConstruct(Map.of("sq_type", "INVALID"));
            item.toDict(VectorField.STAGE_CONSTRUCT);
        }), "sq_type");
    }

    private void caseHnswValidationSqInvalidRefineType() {
        assertValidation(() -> hnsw(item -> {
            item.setVariant("SQ");
            item.setExtraConstruct(mapOf("refine", true, "refine_type", "INVALID"));
            item.toDict(VectorField.STAGE_CONSTRUCT);
        }), "refine_type");
    }

    private void caseHnswValidationPqInvalidM() {
        assertValidation(() -> hnsw(item -> {
            item.setVariant("PQ");
            item.setExtraConstruct(Map.of("m", 0));
            item.toDict(VectorField.STAGE_CONSTRUCT);
        }), "m");
    }

    private void caseHnswValidationPqInvalidNbits() {
        assertValidation(() -> hnsw(item -> {
            item.setVariant("PQ");
            item.setExtraConstruct(mapOf("m", 64, "nbits", 0));
            item.toDict(VectorField.STAGE_CONSTRUCT);
        }), "nbits");
    }

    private void caseHnswValidationPqInvalidRefineK() {
        assertValidation(() -> hnsw(item -> {
            item.setVariant("PQ");
            item.setExtraSearch(Map.of("refine_k", 0.5d));
            item.toDict(VectorField.STAGE_SEARCH);
        }), "refine_k");
    }

    private void caseHnswValidationPrqInvalidNrq() {
        assertValidation(() -> hnsw(item -> {
            item.setVariant("PRQ");
            item.setExtraConstruct(Map.of("nrq", 0));
            item.toDict(VectorField.STAGE_CONSTRUCT);
        }), "nrq");
    }

    private void caseHnswValidationPrqNrqTooHigh() {
        assertValidation(() -> hnsw(item -> {
            item.setVariant("PRQ");
            item.setExtraConstruct(Map.of("nrq", 17));
            item.toDict(VectorField.STAGE_CONSTRUCT);
        }), "nrq");
    }

    private void caseHnswToDictSearch() {
        MilvusHNSW field = hnsw(item -> {
            item.setM(64);
            item.setEfConstruction(400);
            item.setEfSearchFactor(2.0d);
        });
        assertThat(field.toDict(VectorField.STAGE_SEARCH))
                .containsEntry("efSearchFactor", 2.0d)
                .doesNotContainKeys("M", "efConstruction", "database_type", "index_type", "vector_field");
    }

    private void caseHnswToDictConstruct() {
        MilvusHNSW field = hnsw(item -> {
            item.setM(64);
            item.setEfConstruction(400);
            item.setEfSearchFactor(2.0d);
        });
        assertThat(field.toDict(VectorField.STAGE_CONSTRUCT))
                .containsEntry("M", 64)
                .containsEntry("efConstruction", 400)
                .doesNotContainKeys("efSearchFactor", "database_type", "index_type", "vector_field");
    }

    private void caseHnswToDictSearchWithoutEfSearchFactor() {
        MilvusHNSW field = hnsw(item -> {
            item.setM(64);
            item.setEfConstruction(400);
        });
        assertThat(field.toDict(VectorField.STAGE_SEARCH)).doesNotContainKey("efSearchFactor");
    }

    private void caseHnswToDictSearchWithExtraSearch() {
        MilvusHNSW field = hnsw(item -> {
            item.setVariant("PQ");
            item.setEfSearchFactor(2.0d);
            item.setExtraSearch(Map.of("refine_k", 1.5d));
        });
        assertThat(field.toDict(VectorField.STAGE_SEARCH))
                .containsEntry("efSearchFactor", 2.0d)
                .containsEntry("refine_k", 1.5d)
                .doesNotContainKey("extra_search");
    }

    private void caseHnswToDictConstructWithExtraConstruct() {
        MilvusHNSW field = hnsw(item -> {
            item.setVariant("SQ");
            item.setM(64);
            item.setEfConstruction(400);
            item.setExtraConstruct(mapOf("sq_type", "SQ8", "refine", true));
        });
        assertThat(field.toDict(VectorField.STAGE_CONSTRUCT))
                .containsEntry("M", 64)
                .containsEntry("efConstruction", 400)
                .containsEntry("sq_type", "SQ8")
                .containsEntry("refine", true)
                .doesNotContainKey("extra_construct");
    }

    private void caseHnswSqVariantSqTypes() {
        for (String sqType : List.of("SQ4U", "SQ6", "SQ8", "FP16", "BF16")) {
            MilvusHNSW field = hnsw(item -> {
                item.setVariant("SQ");
                item.setExtraConstruct(Map.of("sq_type", sqType));
            });
            assertThat(field.getExtraConstruct()).containsEntry("sq_type", sqType);
        }
    }

    private void caseHnswSqVariantRefineTypes() {
        for (String refineType : List.of("SQ6", "SQ8", "FP16", "BF16", "FP32")) {
            MilvusHNSW field = hnsw(item -> {
                item.setVariant("SQ");
                item.setExtraConstruct(mapOf("refine", true, "refine_type", refineType));
            });
            assertThat(field.getExtraConstruct()).containsEntry("refine_type", refineType);
        }
    }

    private DynamicTest parity(String pythonTestName, Executable executable) {
        return DynamicTest.dynamicTest("Python parity: " + pythonTestName, executable);
    }

    private static MilvusSCANN scann(Consumer<MilvusSCANN> consumer) {
        MilvusSCANN field = new MilvusSCANN();
        consumer.accept(field);
        return field;
    }

    private static MilvusIVF ivf(Consumer<MilvusIVF> consumer) {
        MilvusIVF field = new MilvusIVF();
        consumer.accept(field);
        return field;
    }

    private static MilvusHNSW hnsw(Consumer<MilvusHNSW> consumer) {
        MilvusHNSW field = new MilvusHNSW();
        consumer.accept(field);
        return field;
    }

    private static void assertCommon(VectorField field, String vectorField, String databaseType, String indexType) {
        assertThat(field.getVectorField()).isEqualTo(vectorField);
        assertThat(field.getDatabaseType()).isEqualTo(databaseType);
        assertThat(field.getIndexType()).isEqualTo(indexType);
    }

    private static void assertEmptyNoInternal(Map<String, Object> result) {
        assertThat(result).isEmpty();
        assertThat(result).doesNotContainKeys("database_type", "index_type", "vector_field");
    }

    private static void assertValidation(Executable executable, String messageFragment) {
        assertThatThrownBy(executable::execute)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(messageFragment);
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
