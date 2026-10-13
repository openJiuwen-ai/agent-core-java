/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.deepagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.CheckpointerProvider;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/** Tests downstream checkpointer behavior on a real classpath without Jedis. */
class CheckpointerWithoutJedisTest {
    @BeforeAll
    static void requireClasspathWithoutJedis() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("redis.clients.jedis.Jedis"));
    }

    @Test
    void serviceDiscoverySucceedsWithoutLoadingRedisDependencies() {
        List<String> names = ServiceLoader.load(CheckpointerProvider.class).stream()
                .map(provider -> provider.get().typeName()).toList();
        assertTrue(names.containsAll(List.of("in_memory", "persistence", "redis")));
    }

    @Test
    void defaultCheckpointerWorksWithoutJedis() {
        assertInstanceOf(InMemoryCheckpointer.class, CheckpointerFactory.getCheckpointer());
    }

    @ParameterizedTest
    @ValueSource(strings = {"in_memory", "persistence"})
    void nonRedisCheckpointersSaveAndRecoverGraphState(String type) {
        try (Checkpointer checkpointer = CheckpointerFactory.create(type,
                Map.of("kv_store", new InMemoryKVStore()))) {
            if (type.equals("persistence")) {
                assertInstanceOf(PersistenceCheckpointer.class, checkpointer);
            }
            GraphStoreState state = GraphStoreState.create("graph", 2, Map.of("answer", 42),
                    List.of(), Map.of(), Map.of());
            checkpointer.graphStore().save("session", "graph", state).toCompletableFuture().join();
            GraphStoreState restored = checkpointer.graphStore().get("session", "graph")
                    .toCompletableFuture().join().orElseThrow();
            assertEquals(2, restored.getStep());
            assertEquals(Map.of("answer", 42), restored.getChannelValues());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"redis", "redis_checkpointer_cluster"})
    void explicitRedisSelectionExplainsMissingDependency(String type) {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> CheckpointerFactory.create(type, Map.of()));
        assertTrue(exception.getMessage().contains("redis.clients:jedis"));
        assertInstanceOf(ClassNotFoundException.class, exception.getCause());
    }
}
