package com.openjiuwen.core.memory.support;

import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.retrieval.embedding.HashEmbedding;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.util.UUID;

public final class LongTermMemoryTestSupport {

    private LongTermMemoryTestSupport() {
    }

    public static LongTermMemory registeredMemory() {
        LongTermMemory.resetInstance();
        LongTermMemory memory = LongTermMemory.getInstance();
        memory.registerStore(
                new TestInMemoryKVStore(),
                new InMemoryVectorStore("memory_test_collection_" + UUID.randomUUID()),
                new TestDbStore(createDataSource()),
                new HashEmbedding()
        );
        return memory;
    }

    public static DataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }
}
