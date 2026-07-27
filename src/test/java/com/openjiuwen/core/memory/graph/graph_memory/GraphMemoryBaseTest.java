package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphConstants;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;
import com.openjiuwen.core.memory.config.SearchConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphMemoryBaseTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRegisterStrategyAndSearchEntities() throws Exception {
        GraphConfig config = GraphConfig.builder().uri(tempDir.resolve("graph.db").toString()).backend("in_memory").build();
        GraphMemory memory = new GraphMemory(config);
        memory.attachEmbedder(new DummyEmbedding());
        var backendField = GraphMemory.class.getDeclaredField("dbBackend");
        backendField.setAccessible(true);
        GraphStore store = (GraphStore) backendField.get(memory);
        Entity entity = new Entity();
        entity.setUuid("e1");
        entity.setUserId("user-1");
        entity.setName("Alice");
        entity.setContent("Alice knows Bob");
        entity.setAttributes(Map.of("age", 20));
        entity.setEpisodes(List.of("episode-1"));
        store.addEntity(List.of(entity), false, false, true);

        Relation relation = new Relation();
        relation.setName("knows");
        relation.setUserId("user-1");
        relation.setLhs("e1");
        relation.setRhs("e2");
        relation.setValidSince(123);
        store.addRelation(List.of(relation), false, false, true);

        Episode episode = new Episode();
        episode.setUuid("episode-1");
        episode.setUserId("user-1");
        episode.setContent("Alice met Bob");
        episode.setEntities(List.of("e1", "e2"));
        store.addEpisode(List.of(episode), false, false, true);

        SearchConfig entityConfig = new SearchConfig();
        entityConfig.setTopK(5);
        memory.registerSearchStrategy("custom", entityConfig, new SearchConfig(), new SearchConfig(), true);

        Map<String, List<GraphMemory.SearchHit>> result = memory.search(
                "Alice", "user-1", "custom", true, true, true, null).join();

        assertThat(result).containsKey(GraphConstants.ENTITY_COLLECTION);
        assertThat(result.get(GraphConstants.ENTITY_COLLECTION)).hasSize(1);
        Entity restoredEntity = (Entity) result.get(GraphConstants.ENTITY_COLLECTION).get(0).object();
        assertThat(restoredEntity.getName()).isEqualTo("Alice");
        assertThat(restoredEntity.getAttributes()).containsEntry("age", 20);
        assertThat(restoredEntity.getEpisodes()).containsExactly("episode-1");
        restoredEntity.getEpisodes().add("episode-2");
        assertThat(restoredEntity.getEpisodes()).containsExactly("episode-1", "episode-2");

        Relation restoredRelation = (Relation) result.get(GraphConstants.RELATION_COLLECTION).get(0).object();
        assertThat(restoredRelation.getLhs()).isEqualTo("e1");
        assertThat(restoredRelation.getRhs()).isEqualTo("e2");
        assertThat(restoredRelation.getValidSince()).isEqualTo(123);

        Episode restoredEpisode = (Episode) result.get(GraphConstants.EPISODE_COLLECTION).get(0).object();
        assertThat(restoredEpisode.getContent()).isEqualTo("Alice met Bob");
        assertThat(restoredEpisode.getEntities()).containsExactly("e1", "e2");
    }

    @Test
    void shouldRejectUnknownStrategy() {
        GraphConfig config = GraphConfig.builder().uri(tempDir.resolve("graph.db").toString()).backend("in_memory").build();
        GraphMemory memory = new GraphMemory(config);

        assertThatThrownBy(() -> memory.search("q", "u", "missing", true, false, false, List.of(0.1)).join())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Strategy [missing] not found");
    }

    @Test
    void shouldPrepareConversationEpisodesAndBuildHistory() throws Exception {
        GraphConfig config = GraphConfig.builder().uri(tempDir.resolve("graph.db").toString()).backend("in_memory").build();
        GraphMemory memory = new GraphMemory(config);
        memory.attachEmbedder(new DummyEmbedding());
        var backendField = GraphMemory.class.getDeclaredField("dbBackend");
        backendField.setAccessible(true);
        GraphStore store = (GraphStore) backendField.get(memory);

        Episode existing = new Episode();
        existing.setUuid("ep1");
        existing.setContent("previous conversation");
        existing.setUserId("user-1");
        existing.setValidSince((int) LocalDateTime.now().minusDays(1).toEpochSecond(ZoneOffset.UTC));
        store.addEpisode(List.of(existing), false, false, true);

        Method initState = GraphMemory.class.getDeclaredMethod("initState", Object.class);
        initState.setAccessible(true);
        GraphMemoryStates.GraphMemState state = (GraphMemoryStates.GraphMemState) initState.invoke(memory, (Object) LocalDateTime.now());

        Method prepare = GraphMemory.class.getDeclaredMethod(
                "prepareEpisodes",
                com.openjiuwen.core.memory.config.EpisodeType.class,
                String.class,
                Object.class,
                GraphMemoryStates.GraphMemState.class,
                Map.class
        );
        prepare.setAccessible(true);
        CompletableFuture<String> prepareFuture = (CompletableFuture<String>) prepare.invoke(memory,
                com.openjiuwen.core.memory.config.EpisodeType.CONVERSATION,
                "user-1",
                List.of(Map.of("role", "user", "content", "hello")),
                state,
                Map.of("user", "用户"));
        String content = prepareFuture.join();

        assertThat(content).contains("hello");
        assertThat(state.getHistory()).contains("previous conversation");
    }

    @Test
    void shouldAddMemoryThroughBasicMainFlow() throws Exception {
        GraphConfig config = GraphConfig.builder().uri(tempDir.resolve("graph.db").toString()).backend("in_memory").build();
        FakeLlmInvoker fakeLlm = new FakeLlmInvoker(
                "{\"extracted_relations\":[]}",
                "{\"extracted_entities\":[{\"name\":\"Alice\",\"entityTypeId\":0}]}",
                "{\"extracted_relations\":[{\"name\":\"self_fact\",\"fact\":\"likes coffee\",\"sourceId\":1,\"targetId\":1}]}",
                "{\"summary\":\"Alice likes coffee\",\"attributes\":{\"preference\":\"coffee\"}}"
        );
        GraphMemory memory = new GraphMemory(config, fakeLlm, true, null, null, null, Map.of(), "cn", false);
        memory.attachEmbedder(new DummyEmbedding());

        GraphMemoryStates.GraphMemUpdate update = memory.addMemory(
                com.openjiuwen.core.memory.config.EpisodeType.CONVERSATION,
                "user-1",
                List.of(Map.of("role", "user", "content", "Alice likes coffee")),
                null,
                LocalDateTime.now()
        ).join();

        assertThat(update.getAddedEpisode()).hasSize(1);
        assertThat(update.getAddedEntity()).hasSize(1);
        Entity entity = update.getAddedEntity().get(0);
        assertThat(entity.getName()).isEqualTo("Alice");
        assertThat(entity.getContent()).contains("Alice likes coffee");
    }

    private static final class DummyEmbedding extends Embedding {
        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(0.1, 0.2));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(texts.stream().map(text -> List.of(0.1, 0.2)).toList());
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }

    private static final class FakeLlmInvoker implements GraphMemory.LlmInvoker {
        private final Deque<String> responses;

        private FakeLlmInvoker(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public CompletableFuture<GraphMemory.LlmResponse> invoke(Map<String, Object> params) {
            return CompletableFuture.completedFuture(new GraphMemory.LlmResponse(responses.removeFirst()));
        }
    }
}
