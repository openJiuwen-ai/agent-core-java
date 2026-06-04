/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.graph_memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;
import com.openjiuwen.core.memory.graph.graph_memory.GraphMemory;
import com.openjiuwen.core.memory.graph.graph_memory.GraphMemoryStates;
import com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding;
import com.openjiuwen.examples.graph_memory.memory_data.GraphMemoryDataLoader;
import com.openjiuwen.examples.graph_memory.utils.GraphMemoryExampleConfig;
import com.openjiuwen.examples.graph_memory.utils.GraphMemoryExampleOutput;
import com.openjiuwen.examples.graph_memory.utils.GraphMemoryKnowledgeGraphVisualization;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * End-to-end showcase for GraphMemory.
 *
 * <p>Mirrors Python's {@code examples.graph_memory.showcase_graph_memory}.</p>
 */
public final class GraphMemoryShowcase {

    public static final String USER_ID = "showcase_user";
    public static final boolean MERGE_ENTITIES = true;
    public static final boolean MERGE_RELATIONS = true;
    public static final boolean MERGE_FILTER = true;
    public static final int CHUNK_SIZE = 100;
    public static final int CHUNK_OVERLAP = 2;
    public static final int SUMMARY_TARGET = 100;
    public static final boolean RUN_VISUALIZATION = true;
    public static final boolean SKIP_GRAPH_BUILD = false;
    public static final Path EXAMPLE_ROOT = Path.of("examples", "graph_memory").toAbsolutePath().normalize();
    public static final Path KG_VIS_DIR = EXAMPLE_ROOT.resolve("kg_visualization");
    public static final int SEARCH_TOP_K = 5;
    public static final int SEARCH_CONTENT_MAX_LEN = 55;
    public static final String ENTITY_COLLECTION = "entity";
    public static final String RELATION_COLLECTION = "relation";
    public static final String EPISODE_COLLECTION = "episode";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final List<String> SEARCH_QUERIES_ENTITY = List.of(
            "\u4fe1\u7528\u5361\u652f\u4ed8\u5931\u8d25\u6216\u98ce\u63a7",
            "\u623f\u8d37\u5229\u7387\u548c\u7b49\u989d\u672c\u606f\u6708\u4f9b",
            "\u5de5\u8d44\u7406\u8d22\u548c\u81ea\u52a8\u8f6c\u5b58"
    );
    private static final List<String> SEARCH_QUERIES_RELATION = List.of(
            "\u7528\u6237\u54a8\u8be2\u8fd8\u6b3e\u65b9\u5f0f\u6216\u989d\u5ea6",
            "\u5ba2\u670d\u89e3\u7b54\u8d37\u6b3e\u6216\u7406\u8d22\u95ee\u9898"
    );
    private static final String ALL_COLLECTIONS_QUERY =
            "\u5f20\u4f1f\u5728\u94f6\u884c\u54a8\u8be2\u8fc7\u7684\u95ee\u9898";

    private GraphMemoryShowcase() {
    }

    public static void main(String[] args) {
        run(ShowcaseOptions.defaults(), new DefaultShowcaseRuntime());
    }

    public static ShowcaseResult run(ShowcaseOptions options, ShowcaseRuntime runtime) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(runtime, "runtime");

        runtime.writeOutput("%s", "=".repeat(60));
        runtime.writeOutput("GraphMemory showcase");
        runtime.writeOutput("%s", "=".repeat(60));
        runtime.writeOutput("USER_ID: %s", options.userId());
        runtime.writeOutput("SKIP_GRAPH_BUILD: %s", options.skipGraphBuild());
        if (!options.skipGraphBuild()) {
            runtime.writeOutput(
                    "MERGE_ENTITIES=%s, MERGE_RELATIONS=%s, MERGE_FILTER=%s",
                    options.mergeEntities(),
                    options.mergeRelations(),
                    options.mergeFilter());
            runtime.writeOutput("CHUNK_SIZE=%d, CHUNK_OVERLAP=%d", options.chunkSize(), options.chunkOverlap());
        }
        runtime.writeOutput("");

        runtime.writeOutput("%s", "=".repeat(60));
        runtime.writeOutput("Step 1: Build LLM, embedder, reranker, graph config");
        runtime.writeOutput("%s", "=".repeat(60));
        Object embedder = runtime.buildEmbedder();
        if (embedder == null) {
            runtime.writeOutput("Missing embedding config. Set JIUWEN_GRAPH_MEM_EMBED_URL and JIUWEN_GRAPH_MEM_EMBED_MODEL.");
            return new ShowcaseResult(ShowcaseStatus.MISSING_EMBEDDER, 0);
        }
        Object llm = runtime.buildLlm();
        if (!options.skipGraphBuild() && llm == null) {
            runtime.writeOutput("Missing LLM config. Set JIUWEN_GRAPH_MEM_LLM_URL and JIUWEN_GRAPH_MEM_LLM_MODEL.");
            return new ShowcaseResult(ShowcaseStatus.MISSING_LLM, 0);
        }

        int embedDim = extractEmbedDim(embedder);
        Object reranker = runtime.buildReranker();
        GraphConfig dbConfig = runtime.buildGraphConfig(embedDim);
        runtime.writeOutput("Using LLM: %s", runtime.env("JIUWEN_GRAPH_MEM_LLM_MODEL"));
        runtime.writeOutput("Using embedding: %s (dim=%s)", runtime.env("JIUWEN_GRAPH_MEM_EMBED_MODEL"), embedDim);
        runtime.writeOutput("Reranker: %s", reranker == null ? "disabled" : "enabled");
        runtime.writeOutput("");

        runtime.writeOutput("%s", "=".repeat(60));
        runtime.writeOutput("Step 2: Create GraphMemory%s", options.skipGraphBuild() ? "" : " and rebuild store");
        runtime.writeOutput("%s", "=".repeat(60));

        AddMemoryStrategy strategy = new AddMemoryStrategy(
                options.summaryTarget(),
                options.mergeEntities(),
                options.mergeRelations(),
                options.mergeFilter());
        Map<String, Object> llmConfig = runtime.getEnvJson("JIUWEN_GRAPH_MEM_LLM_CONFIG");
        GraphMemoryClient graphMemory = runtime.createGraphMemory(new GraphMemoryStartup(
                dbConfig,
                llm,
                Boolean.TRUE.equals(llmConfig.get("structured_output")),
                Map.of("extra_body", Map.of("enable_thinking", false)),
                reranker,
                strategy,
                "cn",
                false));

        try {
            if (!options.skipGraphBuild()) {
                try {
                    graphMemory.rebuild();
                    runtime.writeOutput("Store rebuilt: %s", dbConfig.getName());
                } catch (Exception e) {
                    runtime.writeOutput("Failed to rebuild store: %s", e.getMessage());
                    runtime.writeOutput("Ensure Milvus is running at %s", dbConfig.getUri());
                    return new ShowcaseResult(ShowcaseStatus.REBUILD_FAILED, 0);
                }
            }
            runtime.writeOutput("");

            int chunkIndex = 0;
            if (!options.skipGraphBuild()) {
                runtime.writeOutput("%s", "=".repeat(60));
                runtime.writeOutput("Step 3: Load conversations, chunk, add_memory");
                runtime.writeOutput("%s", "=".repeat(60));
                List<String> convFiles = runtime.listDataFiles();
                if (convFiles.isEmpty()) {
                    runtime.writeOutput("No conversation_*.json files in memory_data/mock_data/.");
                    return new ShowcaseResult(ShowcaseStatus.NO_CONVERSATION_FILES, 0);
                }

                runtime.writeOutput("Conversation files: %d", convFiles.size());
                for (String file : convFiles) {
                    List<Map<String, String>> messages = runtime.loadTestData(file);
                    List<List<Map<String, String>>> chunks =
                            GraphMemoryDataLoader.chunkConv(messages, options.chunkSize(), options.chunkOverlap());
                    for (int i = 0; i < chunks.size(); i++) {
                        List<Map<String, String>> chunk = chunks.get(i);
                        chunkIndex += 1;
                        LocalDateTime refTime = LocalDateTime.parse(chunk.getFirst().get("iso_time"));
                        runtime.writeOutput(
                                "Chunk %d (file=%s, slice %d/%d): %d messages, ref_time=%s",
                                chunkIndex,
                                Path.of(file).getFileName(),
                                i + 1,
                                chunks.size(),
                                chunk.size(),
                                refTime.toString().substring(0, Math.min(19, refTime.toString().length())));
                        GraphMemoryStates.GraphMemUpdate update =
                                graphMemory.addMemory(EpisodeType.CONVERSATION, chunk, options.userId(), refTime);
                        runtime.writeOutput(
                                "  added_entity=%d, added_relation=%d, updated_entity=%d, updated_relation=%d",
                                update.getAddedEntity().size(),
                                update.getAddedRelation().size(),
                                update.getUpdatedEntity().size(),
                                update.getUpdatedRelation().size());
                    }
                }
                runtime.writeOutput("");

                runtime.writeOutput("%s", "=".repeat(60));
                runtime.writeOutput("Step 4: Refresh (flush and compact)");
                runtime.writeOutput("%s", "=".repeat(60));
                graphMemory.refresh(false);
                runtime.writeOutput("Refresh completed");
                runtime.writeOutput("");
            }

            runSearchSections(options, runtime, graphMemory);
            runVisualizationIfNeeded(options, runtime, graphMemory, dbConfig);
            runtime.writeOutput("%s", "=".repeat(60));
            runtime.writeOutput("Showcase finished. Store closed.");
            runtime.writeOutput("%s", "=".repeat(60));
            return new ShowcaseResult(ShowcaseStatus.SUCCESS, chunkIndex);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load graph memory showcase data", e);
        } catch (Exception e) {
            throw new IllegalStateException("GraphMemory showcase failed", e);
        } finally {
            graphMemory.close();
        }
    }

    private static void runSearchSections(ShowcaseOptions options,
                                          ShowcaseRuntime runtime,
                                          GraphMemoryClient graphMemory) {
        runtime.writeOutput("%s", "=".repeat(60));
        runtime.writeOutput("Step 5a: Entity search (top-%d per query)", options.searchTopK());
        runtime.writeOutput("%s", "=".repeat(60));
        try {
            for (String query : options.entityQueries()) {
                Map<String, List<SearchHit>> results = graphMemory.search(
                        new SearchRequest(query, options.userId(), true, false, false, options.searchTopK()));
                printHits(runtime, query, "entities", results.getOrDefault(ENTITY_COLLECTION, List.of()), options);
            }
        } catch (Exception e) {
            runtime.writeOutput("Entity search failed: %s", e.getMessage());
        }
        runtime.writeOutput("");

        runtime.writeOutput("%s", "=".repeat(60));
        runtime.writeOutput("Step 5b: Relation search (top-%d per query)", options.searchTopK());
        runtime.writeOutput("%s", "=".repeat(60));
        try {
            for (String query : options.relationQueries()) {
                Map<String, List<SearchHit>> results = graphMemory.search(
                        new SearchRequest(query, options.userId(), false, true, false, options.searchTopK()));
                printHits(runtime, query, "relations", results.getOrDefault(RELATION_COLLECTION, List.of()), options);
            }
        } catch (Exception e) {
            runtime.writeOutput("Relation search failed: %s", e.getMessage());
        }
        runtime.writeOutput("");

        runtime.writeOutput("%s", "=".repeat(60));
        runtime.writeOutput("Step 5c: Search all collections (single query)");
        runtime.writeOutput("%s", "=".repeat(60));
        try {
            Map<String, List<SearchHit>> results = graphMemory.search(
                    new SearchRequest(ALL_COLLECTIONS_QUERY, options.userId(), true, true, true, options.searchTopK()));
            runtime.writeOutput("Query: %s", ALL_COLLECTIONS_QUERY);
            for (String collection : List.of(ENTITY_COLLECTION, RELATION_COLLECTION, EPISODE_COLLECTION)) {
                List<SearchHit> items = results.getOrDefault(collection, List.of());
                runtime.writeOutput("  %s: %d hits", collection, items.size());
                for (SearchHit hit : items.stream().limit(2).toList()) {
                    runtime.writeOutput("    - %s (score=%.4f)", objectName(hit.object()), hit.score());
                }
            }
        } catch (Exception e) {
            runtime.writeOutput("Search failed: %s", e.getMessage());
        }
        runtime.writeOutput("");
    }

    private static void runVisualizationIfNeeded(ShowcaseOptions options,
                                                 ShowcaseRuntime runtime,
                                                 GraphMemoryClient graphMemory,
                                                 GraphConfig dbConfig) {
        if (!options.runVisualization()) {
            runtime.writeOutput("Step 6: Skipped (RUN_VISUALIZATION=False)");
            runtime.writeOutput("");
            return;
        }
        runtime.writeOutput("%s", "=".repeat(60));
        runtime.writeOutput("Step 6: Export and visualize KG");
        runtime.writeOutput("%s", "=".repeat(60));
        try {
            List<Entity> entities = graphMemory.query(ENTITY_COLLECTION, 16000).stream()
                    .map(GraphMemoryShowcase::entityFromRaw)
                    .toList();
            List<Relation> relations = graphMemory.query(RELATION_COLLECTION, 16000).stream()
                    .map(GraphMemoryShowcase::relationFromRaw)
                    .toList();
            List<Episode> episodes = graphMemory.query(EPISODE_COLLECTION, 16000).stream()
                    .map(GraphMemoryShowcase::episodeFromRaw)
                    .toList();
            String outPath = options.kgVisDir().resolve(dbConfig.getName()).toString();
            runtime.visualize(entities, relations, episodes, outPath);
            runtime.writeOutput("KG visualization saved: %s.html", outPath);
        } catch (Exception e) {
            runtime.writeOutput("Visualization skipped: %s", e.getMessage());
        }
        runtime.writeOutput("");
    }

    private static void printHits(ShowcaseRuntime runtime,
                                  String query,
                                  String label,
                                  List<SearchHit> hits,
                                  ShowcaseOptions options) {
        runtime.writeOutput("Query: %s", query);
        runtime.writeOutput("Top %d %s:", Math.min(options.searchTopK(), hits.size()), label);
        for (int i = 0; i < Math.min(options.searchTopK(), hits.size()); i++) {
            SearchHit hit = hits.get(i);
            String content = objectContent(hit.object());
            String prefix = content.substring(0, Math.min(options.searchContentMaxLen(), content.length()));
            String suffix = content.length() > options.searchContentMaxLen() ? "..." : "";
            runtime.writeOutput("  %d. %s (score=%.4f) %s%s", i + 1, objectName(hit.object()), hit.score(), prefix, suffix);
        }
        runtime.writeOutput("");
    }

    private static int extractEmbedDim(Object embedder) {
        if (embedder instanceof DimensionedEmbedding dimensioned) {
            return dimensioned.dimension();
        }
        if (embedder instanceof OpenAIEmbedding openAIEmbedding) {
            return openAIEmbedding.getDimension();
        }
        try {
            Object dimension = embedder.getClass().getMethod("getDimension").invoke(embedder);
            if (dimension instanceof Number number) {
                return number.intValue();
            }
        } catch (ReflectiveOperationException ignored) {
            return 1024;
        }
        return 1024;
    }

    private static String formatChunk(List<Map<String, String>> chunk) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, String> message : chunk) {
            builder.append(message.getOrDefault("role", ""))
                    .append(": ")
                    .append(message.getOrDefault("content", ""))
                    .append('\n');
        }
        return builder.toString();
    }

    private static List<SearchHit> hitsFromObjects(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<SearchHit> hits = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof SearchHit hit) {
                hits.add(hit);
            } else {
                hits.add(new SearchHit(0.0, item));
            }
        }
        return hits;
    }

    private static String objectName(Object object) {
        if (object instanceof Entity entity) {
            return !safe(entity.getName()).isBlank() ? entity.getName() : safe(entity.getUuid()).substring(0, Math.min(8, safe(entity.getUuid()).length()));
        }
        if (object instanceof Relation relation) {
            return !safe(relation.getName()).isBlank() ? relation.getName() : safe(relation.getUuid()).substring(0, Math.min(8, safe(relation.getUuid()).length()));
        }
        if (object instanceof Episode episode) {
            return safe(episode.getUuid()).substring(0, Math.min(8, safe(episode.getUuid()).length()));
        }
        return safe(object);
    }

    private static String objectContent(Object object) {
        if (object instanceof Entity entity) {
            return safe(entity.getContent());
        }
        if (object instanceof Relation relation) {
            return safe(relation.getContent());
        }
        if (object instanceof Episode episode) {
            return safe(episode.getContent());
        }
        return "";
    }

    private static Entity entityFromRaw(Map<String, Object> raw) {
        return OBJECT_MAPPER.convertValue(normalizeGraphKeys(stripEmbeddingFields(raw)), Entity.class);
    }

    private static Relation relationFromRaw(Map<String, Object> raw) {
        return OBJECT_MAPPER.convertValue(normalizeGraphKeys(stripEmbeddingFields(raw)), Relation.class);
    }

    private static Episode episodeFromRaw(Map<String, Object> raw) {
        return OBJECT_MAPPER.convertValue(normalizeGraphKeys(stripEmbeddingFields(raw)), Episode.class);
    }

    private static Map<String, Object> stripEmbeddingFields(Map<String, Object> raw) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        if (raw != null) {
            raw.forEach((key, value) -> {
                if (key == null || !key.endsWith("_embedding")) {
                    cleaned.put(key, value);
                }
            });
        }
        return cleaned;
    }

    private static Map<String, Object> normalizeGraphKeys(Map<String, Object> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        raw.forEach((key, value) -> normalized.put(switch (key) {
            case "created_at" -> "createdAt";
            case "user_id" -> "userId";
            case "obj_type" -> "objType";
            case "content_bm25" -> "contentBm25";
            case "entity_type" -> "entityType";
            case "relation_type" -> "relationType";
            case "episode_type" -> "episodeType";
            case "valid_since" -> "validSince";
            case "valid_until" -> "validUntil";
            case "offset_since" -> "offsetSince";
            case "offset_until" -> "offsetUntil";
            case "lhs" -> "sourceEntityId";
            case "rhs" -> "targetEntityId";
            default -> key;
        }, value));
        return normalized;
    }

    private static Map<String, Object> toMap(Object object) {
        return OBJECT_MAPPER.convertValue(object, MAP_TYPE);
    }

    private static String safe(Object value) {
        return Objects.toString(value, "");
    }

    public record ShowcaseOptions(String userId,
                                  boolean mergeEntities,
                                  boolean mergeRelations,
                                  boolean mergeFilter,
                                  int chunkSize,
                                  int chunkOverlap,
                                  int summaryTarget,
                                  boolean runVisualization,
                                  boolean skipGraphBuild,
                                  Path kgVisDir,
                                  List<String> entityQueries,
                                  List<String> relationQueries,
                                  int searchTopK,
                                  int searchContentMaxLen) {
        public static ShowcaseOptions defaults() {
            return new ShowcaseOptions(
                    USER_ID,
                    MERGE_ENTITIES,
                    MERGE_RELATIONS,
                    MERGE_FILTER,
                    CHUNK_SIZE,
                    CHUNK_OVERLAP,
                    SUMMARY_TARGET,
                    RUN_VISUALIZATION,
                    SKIP_GRAPH_BUILD,
                    KG_VIS_DIR,
                    SEARCH_QUERIES_ENTITY,
                    SEARCH_QUERIES_RELATION,
                    SEARCH_TOP_K,
                    SEARCH_CONTENT_MAX_LEN);
        }
    }

    public record AddMemoryStrategy(int summaryTarget,
                                    boolean mergeEntities,
                                    boolean mergeRelations,
                                    boolean mergeFilter) {
    }

    public record GraphMemoryStartup(GraphConfig dbConfig,
                                     Object llmClient,
                                     boolean llmStructuredOutput,
                                     Map<String, Object> llmExtraKwargs,
                                     Object reranker,
                                     AddMemoryStrategy extractionStrategy,
                                     String language,
                                     boolean debug) {
    }

    public record SearchRequest(String query,
                                String userId,
                                boolean entity,
                                boolean relation,
                                boolean episode,
                                int topK) {
    }

    public record SearchHit(double score, Object object) {
    }

    public record ShowcaseResult(ShowcaseStatus status, int chunksAdded) {
    }

    public enum ShowcaseStatus {
        SUCCESS,
        MISSING_EMBEDDER,
        MISSING_LLM,
        REBUILD_FAILED,
        NO_CONVERSATION_FILES
    }

    public interface DimensionedEmbedding {
        int dimension();
    }

    public interface ShowcaseRuntime {
        Object buildEmbedder();

        Object buildLlm();

        Object buildReranker();

        GraphConfig buildGraphConfig(int embedDim);

        Map<String, Object> getEnvJson(String key);

        String env(String key);

        GraphMemoryClient createGraphMemory(GraphMemoryStartup startup);

        List<String> listDataFiles();

        List<Map<String, String>> loadTestData(String file) throws IOException;

        void visualize(List<Entity> entities, List<Relation> relations, List<Episode> episodes, String outputBase);

        void writeOutput(String format, Object... args);
    }

    public interface GraphMemoryClient {
        void rebuild() throws Exception;

        GraphMemoryStates.GraphMemUpdate addMemory(EpisodeType srcType,
                                                   List<Map<String, String>> content,
                                                   String userId,
                                                   LocalDateTime referenceTime);

        void refresh(boolean skipCompact) throws Exception;

        Map<String, List<SearchHit>> search(SearchRequest request) throws Exception;

        List<Map<String, Object>> query(String collection, int limit) throws Exception;

        void close();
    }

    public static final class DefaultShowcaseRuntime implements ShowcaseRuntime {

        @Override
        public Object buildEmbedder() {
            return GraphMemoryExampleConfig.buildEmbedder();
        }

        @Override
        public Object buildLlm() {
            return GraphMemoryExampleConfig.buildLlm();
        }

        @Override
        public Object buildReranker() {
            return GraphMemoryExampleConfig.buildReranker();
        }

        @Override
        public GraphConfig buildGraphConfig(int embedDim) {
            return GraphMemoryExampleConfig.buildGraphConfig(embedDim);
        }

        @Override
        public Map<String, Object> getEnvJson(String key) {
            return GraphMemoryExampleConfig.getEnvJson(key);
        }

        @Override
        public String env(String key) {
            return System.getenv().getOrDefault(key, "");
        }

        @Override
        public GraphMemoryClient createGraphMemory(GraphMemoryStartup startup) {
            return new ExistingGraphMemoryClient(new GraphMemory());
        }

        @Override
        public List<String> listDataFiles() {
            return GraphMemoryDataLoader.listDataFiles();
        }

        @Override
        public List<Map<String, String>> loadTestData(String file) throws IOException {
            return GraphMemoryDataLoader.loadTestData(file);
        }

        @Override
        public void visualize(List<Entity> entities, List<Relation> relations, List<Episode> episodes, String outputBase) {
            GraphMemoryKnowledgeGraphVisualization.main(entities, relations, episodes, outputBase);
        }

        @Override
        public void writeOutput(String format, Object... args) {
            GraphMemoryExampleOutput.writeOutput(format, args);
        }
    }

    public static final class ExistingGraphMemoryClient implements GraphMemoryClient {
        private final GraphMemory graphMemory;

        public ExistingGraphMemoryClient(GraphMemory graphMemory) {
            this.graphMemory = graphMemory;
        }

        @Override
        public void rebuild() {
        }

        @Override
        public GraphMemoryStates.GraphMemUpdate addMemory(EpisodeType srcType,
                                                          List<Map<String, String>> content,
                                                          String userId,
                                                          LocalDateTime referenceTime) {
            try {
                return graphMemory.add(formatChunk(content), srcType.name()).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("GraphMemory add interrupted", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("GraphMemory add failed", e.getCause());
            }
        }

        @Override
        public void refresh(boolean skipCompact) {
        }

        @Override
        public Map<String, List<SearchHit>> search(SearchRequest request) throws Exception {
            Map<String, Object> raw = graphMemory.search(request.query(), request.topK()).get();
            Map<String, List<SearchHit>> results = new LinkedHashMap<>();
            results.put(ENTITY_COLLECTION, hitsFromObjects(raw.get("entities")));
            results.put(RELATION_COLLECTION, hitsFromObjects(raw.get("relations")));
            results.put(EPISODE_COLLECTION, hitsFromObjects(raw.get("episodes")));
            return results;
        }

        @Override
        public List<Map<String, Object>> query(String collection, int limit) {
            List<?> values = switch (collection) {
                case ENTITY_COLLECTION -> graphMemory.getEntities();
                case RELATION_COLLECTION -> graphMemory.getRelations();
                case EPISODE_COLLECTION -> graphMemory.getEpisodes();
                default -> List.of();
            };
            return values.stream().limit(limit).map(GraphMemoryShowcase::toMap).toList();
        }

        @Override
        public void close() {
        }
    }
}
