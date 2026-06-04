package com.openjiuwen.unit_tests.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphStoreFactory;
import com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreStorageConfig;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.foundation.store.graph.milvus.GenerateMilvusSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestInit {

    @Test
    void testExports() {
        assertAll(
                () -> assertNotNull(GraphStore.class),
                () -> assertNotNull(GraphStoreFactory.class),
                () -> assertNotNull(GraphConfig.class),
                () -> assertNotNull(GraphStoreIndexConfig.class),
                () -> assertNotNull(GraphStoreStorageConfig.class),
                () -> assertNotNull(GenerateMilvusSchema.ENTITY_COLLECTION),
                () -> assertNotNull(GenerateMilvusSchema.EPISODE_COLLECTION),
                () -> assertNotNull(GenerateMilvusSchema.RELATION_COLLECTION),
                () -> assertNotNull(Entity.class),
                () -> assertNotNull(Episode.class),
                () -> assertNotNull(Relation.class));
    }
}
