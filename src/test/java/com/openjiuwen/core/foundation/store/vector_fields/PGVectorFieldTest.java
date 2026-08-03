/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PGVectorFieldTest {

    @Test
    void constructStageExportsOnlyPgBuildParameters() {
        PGVectorField field = new PGVectorField();

        assertThat(field.getDatabaseType()).isEqualTo("pg");
        assertThat(field.getIndexType()).isEqualTo("hnsw");
        assertThat(field.toDict(VectorField.STAGE_CONSTRUCT)).containsExactly(
                Map.entry("m", 16),
                Map.entry("ef_construction", 64),
                Map.entry("lists", 100)
        );
    }

    @Test
    void searchStageExportsSearchParametersAndUnpacksExtraSearch() {
        PGVectorField field = new PGVectorField();
        field.setIndexType("ivfflat");
        Map<String, Object> extraSearch = new LinkedHashMap<>();
        extraSearch.put("distance_metric", "cosine");
        extraSearch.put("target_lists", 8);
        field.setExtraSearch(extraSearch);

        assertThat(field.toDict(VectorField.STAGE_SEARCH)).containsExactly(
                Map.entry("ef_search", 40),
                Map.entry("probes", 1),
                Map.entry("distance_metric", "cosine"),
                Map.entry("target_lists", 8)
        );
    }

    @Test
    void validatorsRejectOutOfRangeValues() {
        PGVectorField field = new PGVectorField();

        assertThatThrownBy(() -> field.setIndexType("flat"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indexType");
        assertThatThrownBy(() -> field.setM(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("m");
        assertThatThrownBy(() -> field.setEfConstruction(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("efConstruction");
        assertThatThrownBy(() -> field.setEfSearch(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("efSearch");
        assertThatThrownBy(() -> field.setLists(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lists");
        assertThatThrownBy(() -> field.setProbes(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probes");
    }
}
