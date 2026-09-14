/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChromaVectorFieldTest {

    @Test
    void constructStageExportsHnswParameters() {
        ChromaVectorField field = new ChromaVectorField();

        assertThat(field.getDatabaseType()).isEqualTo("chroma");
        assertThat(field.getIndexType()).isEqualTo("hnsw");
        assertThat(field.toDict(VectorField.STAGE_CONSTRUCT)).containsExactly(
                Map.entry("max_neighbors", 16),
                Map.entry("ef_construction", 100),
                Map.entry("ef_search", 100.0d)
        );
        assertThat(field.toDict(VectorField.STAGE_SEARCH)).isEmpty();
    }

    @Test
    void searchStageUnpacksExtraSearchArguments() {
        ChromaVectorField field = new ChromaVectorField();
        Map<String, Object> extraSearch = new LinkedHashMap<>();
        extraSearch.put("resize_factor", 1.5d);
        extraSearch.put("num_threads", 4);
        extraSearch.put("batch_size", 16);
        extraSearch.put("sync_threshold", 32);
        field.setExtraSearch(extraSearch);

        assertThat(field.toDict(VectorField.STAGE_SEARCH)).containsExactly(
                Map.entry("resize_factor", 1.5d),
                Map.entry("num_threads", 4),
                Map.entry("batch_size", 16),
                Map.entry("sync_threshold", 32)
        );
    }

    @Test
    void validatorRejectsWrongExtraSearchTypes() {
        ChromaVectorField field = new ChromaVectorField();

        assertThatThrownBy(() -> field.setExtraSearch(Map.of("resize_factor", "bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resize_factor");

        assertThatThrownBy(() -> field.setExtraSearch(Map.of("num_threads", 1.5d)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("num_threads");
    }
}
