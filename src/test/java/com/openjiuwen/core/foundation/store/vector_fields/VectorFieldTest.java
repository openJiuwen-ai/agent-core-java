package com.openjiuwen.core.foundation.store.vector_fields;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VectorFieldTest {

    @Test
    void toDictMergesStageSpecificExtrasAndDropsInternalKeys() {
        DummyVectorField field = new DummyVectorField();
        field.constructValue = 8;
        field.searchValue = 3;
        field.extraConstruct = Map.of("m", 32);
        field.extraSearch = Map.of("refine_k", 1.5);

        assertThat(field.toDict(VectorField.STAGE_SEARCH))
                .containsEntry("search_value", 3)
                .containsEntry("refine_k", 1.5)
                .doesNotContainKeys("construct_value", "extra_search", "database_type", "index_type", "vector_field", "variant");
        assertThat(field.toDict(VectorField.STAGE_CONSTRUCT))
                .containsEntry("construct_value", 8)
                .containsEntry("m", 32)
                .doesNotContainKeys("search_value", "extra_construct", "database_type", "index_type", "vector_field", "variant");
    }

    private static final class DummyVectorField extends VectorField {
        private Integer constructValue;
        private Integer searchValue;
        private Map<String, Object> extraConstruct = Map.of();
        private Map<String, Object> extraSearch = Map.of();

        @Override
        public String getDatabaseType() {
            return "milvus";
        }

        @Override
        public String getIndexType() {
            return "hnsw";
        }

        @Override
        public String getVariant() {
            return "PQ";
        }

        @Override
        public Map<String, Object> toDict(String stage) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("database_type", getDatabaseType());
            raw.put("index_type", getIndexType());
            raw.put("vector_field", getVectorField());
            raw.put("variant", getVariant());
            if (VectorField.STAGE_SEARCH.equals(stage)) {
                raw.put("search_value", searchValue);
                raw.put("extra_search", extraSearch);
            } else {
                raw.put("construct_value", constructValue);
                raw.put("extra_construct", extraConstruct);
            }
            return finalizeDict(raw, stage);
        }
    }
}
