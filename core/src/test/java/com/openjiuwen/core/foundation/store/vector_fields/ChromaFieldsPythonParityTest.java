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
 * Mirrors Python's {@code test_chroma_fields} in
 * {@code tests/unit_tests/core/retrieval/indexing/vector_fields/test_chroma_fields.py}.
 */
class ChromaFieldsPythonParityTest {

    @TestFactory
    List<DynamicTest> pythonChromaVectorFieldParityCases() {
        return List.of(
                parity("TestChromaVectorField::test_init_default", this::caseInitDefault),
                parity("TestChromaVectorField::test_init_custom_vector_field", this::caseInitCustomVectorField),
                parity("TestChromaVectorField::test_init_custom_parameters", this::caseInitCustomParameters),
                parity("TestChromaVectorField::test_init_max_neighbors_min", this::caseInitMaxNeighborsMin),
                parity("TestChromaVectorField::test_init_max_neighbors_max", this::caseInitMaxNeighborsMax),
                parity("TestChromaVectorField::test_init_ef_construction_min", this::caseInitEfConstructionMin),
                parity("TestChromaVectorField::test_init_ef_search_min", this::caseInitEfSearchMin),
                parity("TestChromaVectorField::test_init_ef_search_float", this::caseInitEfSearchFloat),
                parity("TestChromaVectorField::test_init_extra_search_empty", this::caseInitExtraSearchEmpty),
                parity("TestChromaVectorField::test_init_extra_search_valid", this::caseInitExtraSearchValid),
                parity("TestChromaVectorField::test_init_extra_search_partial", this::caseInitExtraSearchPartial),
                parity("TestChromaVectorField::test_validation_max_neighbors_too_low",
                        this::caseValidationMaxNeighborsTooLow),
                parity("TestChromaVectorField::test_validation_max_neighbors_too_high",
                        this::caseValidationMaxNeighborsTooHigh),
                parity("TestChromaVectorField::test_validation_ef_construction_too_low",
                        this::caseValidationEfConstructionTooLow),
                parity("TestChromaVectorField::test_validation_ef_search_too_low",
                        this::caseValidationEfSearchTooLow),
                parity("TestChromaVectorField::test_validation_extra_search_invalid_resize_factor",
                        this::caseValidationExtraSearchInvalidResizeFactor),
                parity("TestChromaVectorField::test_validation_extra_search_invalid_num_threads",
                        this::caseValidationExtraSearchInvalidNumThreads),
                parity("TestChromaVectorField::test_validation_extra_search_invalid_batch_size",
                        this::caseValidationExtraSearchInvalidBatchSize),
                parity("TestChromaVectorField::test_validation_extra_search_invalid_sync_threshold",
                        this::caseValidationExtraSearchInvalidSyncThreshold),
                parity("TestChromaVectorField::test_to_dict_search", this::caseToDictSearch),
                parity("TestChromaVectorField::test_to_dict_construct", this::caseToDictConstruct),
                parity("TestChromaVectorField::test_to_dict_search_with_none_fields",
                        this::caseToDictSearchWithNoneFields),
                parity("TestChromaVectorField::test_to_dict_construct_with_none_fields",
                        this::caseToDictConstructWithNoneFields),
                parity("TestChromaVectorField::test_extra_search_merged_in_to_dict",
                        this::caseExtraSearchMergedInToDict)
        );
    }

    private void caseInitDefault() {
        ChromaVectorField field = new ChromaVectorField();

        assertThat(field.getVectorField()).isEqualTo("embedding");
        assertThat(field.getDatabaseType()).isEqualTo("chroma");
        assertThat(field.getIndexType()).isEqualTo("hnsw");
        assertThat(field.getMaxNeighbors()).isEqualTo(16);
        assertThat(field.getEfConstruction()).isEqualTo(100);
        assertThat(field.getEfSearch()).isEqualTo(100.0d);
        assertThat(field.getExtraSearch()).isEmpty();
    }

    private void caseInitCustomVectorField() {
        ChromaVectorField field = field(item -> item.setVectorField("custom_embedding"));

        assertThat(field.getVectorField()).isEqualTo("custom_embedding");
        assertThat(field.getDatabaseType()).isEqualTo("chroma");
        assertThat(field.getIndexType()).isEqualTo("hnsw");
    }

    private void caseInitCustomParameters() {
        ChromaVectorField field = field(item -> {
            item.setVectorField("embeddings");
            item.setMaxNeighbors(32);
            item.setEfConstruction(200);
            item.setEfSearch(150.5d);
        });

        assertThat(field.getVectorField()).isEqualTo("embeddings");
        assertThat(field.getMaxNeighbors()).isEqualTo(32);
        assertThat(field.getEfConstruction()).isEqualTo(200);
        assertThat(field.getEfSearch()).isEqualTo(150.5d);
    }

    private void caseInitMaxNeighborsMin() {
        assertThat(field(item -> item.setMaxNeighbors(2)).getMaxNeighbors()).isEqualTo(2);
    }

    private void caseInitMaxNeighborsMax() {
        assertThat(field(item -> item.setMaxNeighbors(2048)).getMaxNeighbors()).isEqualTo(2048);
    }

    private void caseInitEfConstructionMin() {
        assertThat(field(item -> item.setEfConstruction(1)).getEfConstruction()).isEqualTo(1);
    }

    private void caseInitEfSearchMin() {
        assertThat(field(item -> item.setEfSearch(1)).getEfSearch()).isEqualTo(1.0d);
    }

    private void caseInitEfSearchFloat() {
        assertThat(field(item -> item.setEfSearch(50.5d)).getEfSearch()).isEqualTo(50.5d);
    }

    private void caseInitExtraSearchEmpty() {
        assertThat(field(item -> item.setExtraSearch(Map.of())).getExtraSearch()).isEmpty();
    }

    private void caseInitExtraSearchValid() {
        Map<String, Object> extraSearch = mapOf(
                "resize_factor", 2.0d,
                "num_threads", 4,
                "batch_size", 100,
                "sync_threshold", 10
        );
        ChromaVectorField field = field(item -> item.setExtraSearch(extraSearch));

        assertThat(field.getExtraSearch()).containsExactlyEntriesOf(extraSearch);
    }

    private void caseInitExtraSearchPartial() {
        ChromaVectorField field = field(item -> item.setExtraSearch(Map.of("num_threads", 8)));

        assertThat(field.getExtraSearch()).containsEntry("num_threads", 8);
    }

    private void caseValidationMaxNeighborsTooLow() {
        assertValidation(() -> field(item -> item.setMaxNeighbors(1)), "greater_than_equal", "max_neighbors");
    }

    private void caseValidationMaxNeighborsTooHigh() {
        assertValidation(() -> field(item -> item.setMaxNeighbors(2049)), "less_than_equal", "max_neighbors");
    }

    private void caseValidationEfConstructionTooLow() {
        assertValidation(() -> field(item -> item.setEfConstruction(0)), "greater_than_equal", "ef_construction");
    }

    private void caseValidationEfSearchTooLow() {
        assertValidation(() -> field(item -> item.setEfSearch(0.5d)), "greater_than_equal", "ef_search");
    }

    private void caseValidationExtraSearchInvalidResizeFactor() {
        assertValidation(() -> field(item -> item.setExtraSearch(Map.of("resize_factor", "invalid"))),
                "invalid_resize_factor");
    }

    private void caseValidationExtraSearchInvalidNumThreads() {
        assertValidation(() -> field(item -> item.setExtraSearch(Map.of("num_threads", "invalid"))),
                "invalid_num_threads");
    }

    private void caseValidationExtraSearchInvalidBatchSize() {
        assertValidation(() -> field(item -> item.setExtraSearch(Map.of("batch_size", "invalid"))),
                "invalid_batch_size");
    }

    private void caseValidationExtraSearchInvalidSyncThreshold() {
        assertValidation(() -> field(item -> item.setExtraSearch(Map.of("sync_threshold", "invalid"))),
                "invalid_sync_threshold");
    }

    private void caseToDictSearch() {
        ChromaVectorField field = field(item -> {
            item.setMaxNeighbors(32);
            item.setEfConstruction(200);
            item.setEfSearch(150);
            item.setExtraSearch(Map.of("num_threads", 4));
        });
        Map<String, Object> result = field.toDict(VectorField.STAGE_SEARCH);

        assertThat(result).containsEntry("num_threads", 4);
        assertThat(result).doesNotContainKeys(
                "max_neighbors", "ef_construction", "ef_search",
                "database_type", "index_type", "vector_field", "extra_search"
        );
    }

    private void caseToDictConstruct() {
        ChromaVectorField field = field(item -> {
            item.setMaxNeighbors(32);
            item.setEfConstruction(200);
            item.setEfSearch(150);
            item.setExtraSearch(Map.of("num_threads", 4));
        });
        Map<String, Object> result = field.toDict(VectorField.STAGE_CONSTRUCT);

        assertThat(result)
                .containsEntry("max_neighbors", 32)
                .containsEntry("ef_construction", 200)
                .containsEntry("ef_search", 150.0d);
        assertThat(result).doesNotContainKeys(
                "database_type", "index_type", "vector_field", "extra_construct", "extra_search"
        );
    }

    private void caseToDictSearchWithNoneFields() {
        ChromaVectorField field = field(item -> {
            item.setMaxNeighbors(32);
            item.setEfConstruction(200);
        });

        assertThat(field.toDict(VectorField.STAGE_SEARCH)).isEmpty();
    }

    private void caseToDictConstructWithNoneFields() {
        ChromaVectorField field = field(item -> {
            item.setMaxNeighbors(32);
            item.setEfConstruction(200);
        });
        Map<String, Object> result = field.toDict(VectorField.STAGE_CONSTRUCT);

        assertThat(result).containsKeys("max_neighbors", "ef_construction");
    }

    private void caseExtraSearchMergedInToDict() {
        ChromaVectorField field = field(item -> {
            item.setEfSearch(100);
            item.setExtraSearch(mapOf("resize_factor", 2.0d, "num_threads", 4));
        });
        Map<String, Object> result = field.toDict(VectorField.STAGE_SEARCH);

        assertThat(result).containsEntry("resize_factor", 2.0d).containsEntry("num_threads", 4);
        assertThat(result).doesNotContainKeys("ef_search", "extra_search");
    }

    private DynamicTest parity(String pythonTestName, Executable executable) {
        return DynamicTest.dynamicTest("Python parity: " + pythonTestName, executable);
    }

    private static ChromaVectorField field(Consumer<ChromaVectorField> consumer) {
        ChromaVectorField field = new ChromaVectorField();
        consumer.accept(field);
        return field;
    }

    private static void assertValidation(Executable executable, String... messageFragments) {
        assertThatThrownBy(executable::execute)
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(error -> {
                    for (String fragment : messageFragments) {
                        assertThat(error.getMessage()).contains(fragment);
                    }
                });
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
