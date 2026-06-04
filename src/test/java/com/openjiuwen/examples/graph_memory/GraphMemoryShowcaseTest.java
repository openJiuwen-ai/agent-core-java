/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.graph_memory;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;
import com.openjiuwen.core.memory.graph.graph_memory.GraphMemoryStates;
import com.openjiuwen.examples.graph_memory.GraphMemoryShowcase.AddMemoryStrategy;
import com.openjiuwen.examples.graph_memory.GraphMemoryShowcase.DimensionedEmbedding;
import com.openjiuwen.examples.graph_memory.GraphMemoryShowcase.GraphMemoryClient;
import com.openjiuwen.examples.graph_memory.GraphMemoryShowcase.GraphMemoryStartup;
import com.openjiuwen.examples.graph_memory.GraphMemoryShowcase.SearchHit;
import com.openjiuwen.examples.graph_memory.GraphMemoryShowcase.SearchRequest;
import com.openjiuwen.examples.graph_memory.GraphMemoryShowcase.ShowcaseOptions;
import com.openjiuwen.examples.graph_memory.GraphMemoryShowcase.ShowcaseResult;
import com.openjiuwen.examples.graph_memory.GraphMemoryShowcase.ShowcaseRuntime;
import com.openjiuwen.examples.graph_memory.GraphMemoryShowcase.ShowcaseStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphMemoryShowcaseTest {

    @TempDir
    Path tempDir;

    @Test
    void runStopsWhenEmbedderConfigIsMissing() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.embedder = null;

        ShowcaseResult result = GraphMemoryShowcase.run(testOptions(false, false), runtime);

        assertThat(result.status()).isEqualTo(ShowcaseStatus.MISSING_EMBEDDER);
        assertThat(runtime.graphMemoryCreated).isFalse();
        assertThat(runtime.output).anyMatch(line -> line.contains("Missing embedding config"));
    }

    @Test
    void runStopsWhenLlmConfigIsMissingAndGraphBuildIsEnabled() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.llm = null;

        ShowcaseResult result = GraphMemoryShowcase.run(testOptions(false, false), runtime);

        assertThat(result.status()).isEqualTo(ShowcaseStatus.MISSING_LLM);
        assertThat(runtime.graphMemoryCreated).isFalse();
        assertThat(runtime.output).anyMatch(line -> line.contains("Missing LLM config"));
    }

    @Test
    void runBuildsStoreLoadsChunksRefreshesSearchesVisualizesAndCloses() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.files = List.of("conversation_0001.json");
        runtime.messages = List.of(
                message("User", "a", "2026-05-30T10:00:00"),
                message("Assistant", "b", "2026-05-30T10:01:00"),
                message("User", "c", "2026-05-30T10:02:00")
        );
        runtime.client.entityHits = List.of(new SearchHit(0.9, entity("e1", "Alice", "person")));
        runtime.client.relationHits = List.of(new SearchHit(0.8, relation("r1", "works_on", "e1", "e1")));
        runtime.client.episodeHits = List.of(new SearchHit(0.7, episode("ep1")));
        runtime.client.rawEntities = List.of(Map.of(
                "uuid", "e1",
                "name", "Alice",
                "entity_type", "person",
                "content", "content",
                "name_embedding", List.of(1, 2, 3)
        ));
        runtime.client.rawRelations = List.of(Map.of(
                "uuid", "r1",
                "name", "works_on",
                "lhs", "e1",
                "rhs", "e1",
                "content", "content"
        ));
        runtime.client.rawEpisodes = List.of(Map.of("uuid", "ep1", "content", "episode"));

        ShowcaseResult result = GraphMemoryShowcase.run(testOptions(true, false), runtime);

        assertThat(result.status()).isEqualTo(ShowcaseStatus.SUCCESS);
        assertThat(result.chunksAdded()).isEqualTo(2);
        assertThat(runtime.client.rebuilt).isTrue();
        assertThat(runtime.client.addedChunks).hasSize(2);
        assertThat(runtime.client.addedChunks.get(0)).hasSize(2);
        assertThat(runtime.client.addedChunks.get(1)).hasSize(2);
        assertThat(runtime.client.refTimes).containsExactly(
                LocalDateTime.parse("2026-05-30T10:00:00"),
                LocalDateTime.parse("2026-05-30T10:01:00"));
        assertThat(runtime.client.refreshed).isTrue();
        assertThat(runtime.client.closed).isTrue();
        assertThat(runtime.searchRequests).hasSize(3);
        assertThat(runtime.visualized).isTrue();
        assertThat(runtime.visualizedEntities).hasSize(1);
        assertThat(runtime.visualizedRelations.getFirst().getLhs()).isEqualTo("e1");
    }

    @Test
    void runClosesGraphMemoryWhenNoConversationFilesExist() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.files = List.of();

        ShowcaseResult result = GraphMemoryShowcase.run(testOptions(false, false), runtime);

        assertThat(result.status()).isEqualTo(ShowcaseStatus.NO_CONVERSATION_FILES);
        assertThat(runtime.client.closed).isTrue();
        assertThat(runtime.client.refreshed).isFalse();
    }

    @Test
    void skipGraphBuildSkipsRebuildLoadAddAndRefreshButStillSearches() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.llm = null;

        ShowcaseResult result = GraphMemoryShowcase.run(testOptions(false, true), runtime);

        assertThat(result.status()).isEqualTo(ShowcaseStatus.SUCCESS);
        assertThat(runtime.client.rebuilt).isFalse();
        assertThat(runtime.client.addedChunks).isEmpty();
        assertThat(runtime.client.refreshed).isFalse();
        assertThat(runtime.searchRequests).hasSize(3);
    }

    @Test
    void runPassesStrategyAndStartupSettingsToRuntime() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.files = List.of();

        GraphMemoryShowcase.run(testOptions(false, false), runtime);

        GraphMemoryStartup startup = runtime.startup;
        AddMemoryStrategy strategy = startup.extractionStrategy();
        assertThat(startup.llmStructuredOutput()).isTrue();
        assertThat(startup.llmExtraKwargs()).containsKey("extra_body");
        assertThat(startup.language()).isEqualTo("cn");
        assertThat(strategy.summaryTarget()).isEqualTo(100);
        assertThat(strategy.mergeEntities()).isTrue();
        assertThat(strategy.mergeRelations()).isTrue();
        assertThat(strategy.mergeFilter()).isTrue();
    }

    private ShowcaseOptions testOptions(boolean runVisualization, boolean skipGraphBuild) {
        return new ShowcaseOptions(
                GraphMemoryShowcase.USER_ID,
                true,
                true,
                true,
                2,
                1,
                100,
                runVisualization,
                skipGraphBuild,
                tempDir,
                List.of("entity query"),
                List.of("relation query"),
                5,
                12);
    }

    private static Map<String, String> message(String role, String content, String isoTime) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        message.put("iso_time", isoTime);
        return message;
    }

    private static Entity entity(String uuid, String name, String type) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        entity.setEntityType(type);
        entity.setContent("entity content");
        return entity;
    }

    private static Relation relation(String uuid, String name, String lhs, String rhs) {
        Relation relation = new Relation();
        relation.setUuid(uuid);
        relation.setName(name);
        relation.setLhs(lhs);
        relation.setRhs(rhs);
        relation.setContent("relation content");
        return relation;
    }

    private static Episode episode(String uuid) {
        Episode episode = new Episode();
        episode.setUuid(uuid);
        episode.setContent("episode content");
        return episode;
    }

    private static final class FakeEmbedding implements DimensionedEmbedding {
        @Override
        public int dimension() {
            return 256;
        }
    }

    private final class FakeRuntime implements ShowcaseRuntime {
        private Object embedder = new FakeEmbedding();
        private Object llm = new Object();
        private Object reranker = new Object();
        private List<String> files = List.of("conversation_0001.json");
        private List<Map<String, String>> messages = List.of(message("User", "hello", "2026-05-30T10:00:00"));
        private final FakeGraphMemoryClient client = new FakeGraphMemoryClient();
        private final List<String> output = new ArrayList<>();
        private final List<SearchRequest> searchRequests = client.searchRequests;
        private boolean graphMemoryCreated;
        private boolean visualized;
        private GraphMemoryStartup startup;
        private List<Entity> visualizedEntities = List.of();
        private List<Relation> visualizedRelations = List.of();

        @Override
        public Object buildEmbedder() {
            return embedder;
        }

        @Override
        public Object buildLlm() {
            return llm;
        }

        @Override
        public Object buildReranker() {
            return reranker;
        }

        @Override
        public GraphConfig buildGraphConfig(int embedDim) {
            assertThat(embedDim).isEqualTo(256);
            return GraphConfig.builder()
                    .uri(tempDir.resolve("graph.db").toString())
                    .name("graph_memory_test")
                    .embedDim(embedDim)
                    .build();
        }

        @Override
        public Map<String, Object> getEnvJson(String key) {
            return Map.of("structured_output", true);
        }

        @Override
        public String env(String key) {
            return switch (key) {
                case "JIUWEN_GRAPH_MEM_LLM_MODEL" -> "llm-model";
                case "JIUWEN_GRAPH_MEM_EMBED_MODEL" -> "embed-model";
                default -> "";
            };
        }

        @Override
        public GraphMemoryClient createGraphMemory(GraphMemoryStartup startup) {
            this.startup = startup;
            this.graphMemoryCreated = true;
            return client;
        }

        @Override
        public List<String> listDataFiles() {
            return files;
        }

        @Override
        public List<Map<String, String>> loadTestData(String file) throws IOException {
            return messages;
        }

        @Override
        public void visualize(List<Entity> entities, List<Relation> relations, List<Episode> episodes, String outputBase) {
            visualized = true;
            visualizedEntities = entities;
            visualizedRelations = relations;
        }

        @Override
        public void writeOutput(String format, Object... args) {
            output.add(args == null || args.length == 0 ? format : String.format(format, args));
        }
    }

    private final class FakeGraphMemoryClient implements GraphMemoryClient {
        private final List<List<Map<String, String>>> addedChunks = new ArrayList<>();
        private final List<LocalDateTime> refTimes = new ArrayList<>();
        private boolean rebuilt;
        private boolean refreshed;
        private boolean closed;
        private final List<SearchRequest> searchRequests = new ArrayList<>();
        private List<SearchHit> entityHits = List.of();
        private List<SearchHit> relationHits = List.of();
        private List<SearchHit> episodeHits = List.of();
        private List<Map<String, Object>> rawEntities = List.of();
        private List<Map<String, Object>> rawRelations = List.of();
        private List<Map<String, Object>> rawEpisodes = List.of();

        @Override
        public void rebuild() {
            rebuilt = true;
        }

        @Override
        public GraphMemoryStates.GraphMemUpdate addMemory(EpisodeType srcType,
                                                          List<Map<String, String>> content,
                                                          String userId,
                                                          LocalDateTime referenceTime) {
            assertThat(srcType).isEqualTo(EpisodeType.CONVERSATION);
            assertThat(userId).isEqualTo(GraphMemoryShowcase.USER_ID);
            addedChunks.add(content);
            refTimes.add(referenceTime);
            GraphMemoryStates.GraphMemUpdate update = new GraphMemoryStates.GraphMemUpdate();
            update.getAddedEntity().add(entity("added", "Added", "person"));
            update.getAddedRelation().add(relation("rel", "rel", "added", "added"));
            return update;
        }

        @Override
        public void refresh(boolean skipCompact) {
            assertThat(skipCompact).isFalse();
            refreshed = true;
        }

        @Override
        public Map<String, List<SearchHit>> search(SearchRequest request) {
            searchRequests.add(request);
            Map<String, List<SearchHit>> result = new LinkedHashMap<>();
            if (request.entity()) {
                result.put(GraphMemoryShowcase.ENTITY_COLLECTION, entityHits);
            }
            if (request.relation()) {
                result.put(GraphMemoryShowcase.RELATION_COLLECTION, relationHits);
            }
            if (request.episode()) {
                result.put(GraphMemoryShowcase.EPISODE_COLLECTION, episodeHits);
            }
            return result;
        }

        @Override
        public List<Map<String, Object>> query(String collection, int limit) {
            return switch (collection) {
                case GraphMemoryShowcase.ENTITY_COLLECTION -> rawEntities;
                case GraphMemoryShowcase.RELATION_COLLECTION -> rawRelations;
                case GraphMemoryShowcase.EPISODE_COLLECTION -> rawEpisodes;
                default -> List.of();
            };
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
