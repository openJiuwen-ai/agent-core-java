
package com.openjiuwen.extensions.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.store.StoreFactory;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.VectorStoreFactory;
import com.openjiuwen.extensions.store.db.GaussDbStore;

import org.junit.jupiter.api.Test;

import java.util.Map;

import javax.sql.DataSource;

class StoreExtensionCompatibilityTest {
    @Test
    void gaussDbStoreShouldExposeWrappedDataSource() {
        GaussDbStore store = new GaussDbStore("jdbc:h2:mem:test_store");

        DataSource engine = store.getEngine();

        assertThat(engine).isNotNull();
    }

    @Test
    void foundationStoreFactoryShouldCreateElasticsearchVectorStore() {
        Object store = StoreFactory.createVectorStore("elasticsearch", Map.of("collection_name", "demo_collection"));

        assertThat(store).isInstanceOf(com.openjiuwen.core.foundation.store.vector.ElasticsearchVectorStore.class);
    }

    @Test
    void retrievalVectorStoreFactoryShouldAcceptElasticsearchStoreType() {
        VectorStoreConfig config = new VectorStoreConfig(StoreType.ELASTICSEARCH.value(), "demo_collection");

        Object store = VectorStoreFactory.createVectorStore(config);

        assertThat(store).isInstanceOf(com.openjiuwen.core.retrieval.vector_store.ElasticsearchVectorStore.class);
    }
}
