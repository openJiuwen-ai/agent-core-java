/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MilvusVectorFieldTest {

    @Test
    void flatAndAutoExportEmptyStageMaps() {
        assertThat(new MilvusFLAT().toDict(VectorField.STAGE_CONSTRUCT)).isEmpty();
        assertThat(new MilvusAUTO().toDict(VectorField.STAGE_SEARCH)).isEmpty();
    }

    @Test
    void scannExportsConstructAndSearchFields() {
        MilvusSCANN field = new MilvusSCANN();
        field.setReorderK(12);

        assertThat(field.toDict(VectorField.STAGE_CONSTRUCT)).containsExactly(
                Map.entry("nlist", 128),
                Map.entry("with_raw_data", true)
        );
        assertThat(field.toDict(VectorField.STAGE_SEARCH)).containsExactly(
                Map.entry("nprobe", 8),
                Map.entry("reorder_k", 12)
        );
    }

    @Test
    void ivfValidationRejectsInvalidConfigurations() {
        MilvusIVF invalidProbe = new MilvusIVF();
        invalidProbe.setNlist(4);
        invalidProbe.setNprobe(5);

        assertThatThrownBy(() -> invalidProbe.toDict(VectorField.STAGE_SEARCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nprobe must be <= nlist");

        MilvusIVF rabitq = new MilvusIVF();
        rabitq.setVariant("RABITQ");
        rabitq.setExtraSearch(Map.of("rbq_query_bits", 9));

        assertThatThrownBy(() -> rabitq.toDict(VectorField.STAGE_SEARCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rbq_query_bits");
    }

    @Test
    void ivfSearchAndConstructExportsMirrorPythonStageFiltering() {
        MilvusIVF field = new MilvusIVF();
        field.setVariant("PQ");
        field.setExtraConstruct(Map.of("m", 8, "nbits", 6));

        assertThat(field.toDict(VectorField.STAGE_CONSTRUCT)).containsExactly(
                Map.entry("nlist", 128),
                Map.entry("m", 8),
                Map.entry("nbits", 6)
        );
        assertThat(field.toDict(VectorField.STAGE_SEARCH)).containsExactly(
                Map.entry("nprobe", 8)
        );
    }

    @Test
    void hnswValidationAndExportsMirrorVariantRules() {
        MilvusHNSW field = new MilvusHNSW();
        field.setVariant("PQ");
        Map<String, Object> extraConstruct = new LinkedHashMap<>();
        extraConstruct.put("m", 16);
        extraConstruct.put("nbits", 8);
        field.setExtraConstruct(extraConstruct);
        field.setExtraSearch(Map.of("refine_k", 2.0d));
        field.setEfSearchFactor(1.5d);

        assertThat(field.toDict(VectorField.STAGE_CONSTRUCT)).containsExactly(
                Map.entry("M", 30),
                Map.entry("efConstruction", 360),
                Map.entry("m", 16),
                Map.entry("nbits", 8)
        );
        assertThat(field.toDict(VectorField.STAGE_SEARCH)).containsExactly(
                Map.entry("efSearchFactor", 1.5d),
                Map.entry("refine_k", 2.0d)
        );

        field.setVariant("SQ");
        field.setExtraConstruct(Map.of("sq_type", "BAD"));
        field.setExtraSearch(Map.of());

        assertThatThrownBy(() -> field.toDict(VectorField.STAGE_CONSTRUCT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sq_type");
    }
}
