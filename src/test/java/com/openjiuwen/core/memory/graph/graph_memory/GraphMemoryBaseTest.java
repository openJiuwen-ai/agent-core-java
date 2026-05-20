package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphConstants;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.memory.config.graph.SearchConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;

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
        entity.setName("Alice");
        entity.setContent("Alice knows Bob");
        store.addEntity(List.of(entity), false, false, true);

        SearchConfig entityConfig = new SearchConfig();
        entityConfig.setTopK(5);
        memory.registerSearchStrategy("custom", entityConfig, new SearchConfig(), new SearchConfig(), true);

        Map<String, List<GraphMemory.SearchHit>> result = memory.search("Alice", "user-1", "custom", true, false, false, null);

        assertThat(result).containsKey(GraphConstants.ENTITY_COLLECTION);
        assertThat(result.get(GraphConstants.ENTITY_COLLECTION)).hasSize(1);
        assertThat(((Entity) result.get(GraphConstants.ENTITY_COLLECTION).get(0).object()).getName()).isEqualTo("Alice");
    }

    @Test
    void shouldRejectUnknownStrategy() {
        GraphConfig config = GraphConfig.builder().uri(tempDir.resolve("graph.db").toString()).backend("in_memory").build();
        GraphMemory memory = new GraphMemory(config);

        assertThatThrownBy(() -> memory.search("q", "u", "missing", true, false, false, List.of(0.1f)))
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
        existing.setValidSince((int) OffsetDateTime.now().minusDays(1).toEpochSecond());
        store.addEpisode(List.of(existing), false, false, true);

        Method initState = GraphMemory.class.getDeclaredMethod("initState", OffsetDateTime.class);
        initState.setAccessible(true);
        States.GraphMemState state = (States.GraphMemState) initState.invoke(memory, OffsetDateTime.now());

        Method prepare = GraphMemory.class.getDeclaredMethod(
                "prepareEpisodes",
                com.openjiuwen.core.memory.config.graph.EpisodeType.class,
                String.class,
                Object.class,
                States.GraphMemState.class,
                Map.class
        );
        prepare.setAccessible(true);
        String content = (String) prepare.invoke(memory,
                com.openjiuwen.core.memory.config.graph.EpisodeType.CONVERSATION,
                "user-1",
                List.of(Map.of("role", "user", "content", "hello")),
                state,
                Map.of("user", "用户"));

        assertThat(content).contains("hello");
        assertThat(state.getHistory()).contains("previous conversation");
    }

    @Test
    void shouldAddMemoryThroughBasicMainFlow() throws Exception {
        GraphConfig config = GraphConfig.builder().uri(tempDir.resolve("graph.db").toString()).backend("in_memory").build();
        FakeModel fakeModel = new FakeModel(
                "{\"extracted_relations\":[]}",
                "{\"extracted_entities\":[{\"name\":\"Alice\",\"entityTypeId\":0}]}",
                "{\"extracted_relations\":[{\"name\":\"self_fact\",\"fact\":\"likes coffee\",\"sourceId\":1,\"targetId\":1}]}",
                "{\"summary\":\"Alice likes coffee\",\"attributes\":{\"preference\":\"coffee\"}}"
        );
        GraphMemory memory = new GraphMemory(config, fakeModel, true, null, null, Map.of(), null, "cn", false);
        memory.attachEmbedder(new DummyEmbedding());

        States.GraphMemUpdate update = memory.addMemory(
                com.openjiuwen.core.memory.config.graph.EpisodeType.CONVERSATION,
                "user-1",
                List.of(Map.of("role", "user", "content", "Alice likes coffee")),
                null,
                OffsetDateTime.now()
        );

        assertThat(update.getAddedEpisode()).hasSize(1);
        assertThat(update.getAddedEntity()).hasSize(1);
        Entity entity = update.getAddedEntity().get(0);
        assertThat(entity.getName()).isEqualTo("Alice");
        assertThat(entity.getContent()).contains("Alice likes coffee");
    }

    private static final class DummyEmbedding extends Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return List.of(0.1f, 0.2f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            return texts.stream().map(text -> List.of(0.1f, 0.2f)).toList();
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }

    private static final class FakeModel extends Model {
        private final Deque<String> responses;

        private FakeModel(String... responses) {
            super(
                    ModelClientConfig.builder()
                            .clientProvider("OpenAI")
                            .apiKey("test")
                            .apiBase("https://example.invalid/v1")
                            .build(),
                    ModelRequestConfig.builder().build()
            );
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public AssistantMessage invoke(Object messages,
                                       Object tools,
                                       Float temperature,
                                       Float topP,
                                       String model,
                                       Integer maxTokens,
                                       String stop,
                                       com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser outputParser,
                                       Float timeout,
                                       Map<String, Object> kwargs) {
            return AssistantMessage.builder().content(responses.removeFirst()).build();
        }
    }
}
