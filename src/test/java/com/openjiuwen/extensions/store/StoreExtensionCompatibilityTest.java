package com.openjiuwen.extensions.store;

import com.openjiuwen.core.foundation.store.StoreFactory;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.VectorStoreFactory;
import com.openjiuwen.extensions.store.db.GaussDbStore;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreExtensionCompatibilityTest {

    @Test
    void gaussDbStoreShouldExposeWrappedDataSource() {
        DataSource mockDataSource = org.mockito.Mockito.mock(DataSource.class);
        GaussDbStore store = new GaussDbStore(mockDataSource);

        DataSource engine = store.getAsyncEngine();

        assertThat(engine).isNotNull();
        assertThat(engine).isSameAs(mockDataSource);
    }

    @Test
    void foundationStoreFactoryShouldCreateElasticsearchVectorStore() {
        Object store = StoreFactory.createVectorStore("elasticsearch", Map.of(
                "collection_name", "demo_collection"
        ));

        assertThat(store).isInstanceOf(com.openjiuwen.core.foundation.store.vector.ElasticsearchVectorStore.class);
    }

    @Test
    void retrievalVectorStoreFactoryShouldRejectUnsupportedStoreType() {
        VectorStoreConfig config = new VectorStoreConfig("elasticsearch", "demo_collection");

        assertThatThrownBy(() -> VectorStoreFactory.createVectorStore(config))
                .isInstanceOf(Exception.class);
    }
}
