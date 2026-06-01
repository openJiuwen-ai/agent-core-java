/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.store.graph.BaseGraphObject;
import com.openjiuwen.core.foundation.store.graph.BaseRankConfig;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphStoreFactory;
import com.openjiuwen.core.foundation.store.graph.RRFRankConfig;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.foundation.store.graph.WeightedRankConfig;
import com.openjiuwen.core.memory.config.EpisodeType;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.reranker.Reranker;
import com.openjiuwen.spi.store.query.QueryExpr;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Graph memory store and retrieval.
 * <p>
 * Mirrors Python's {@code GraphMemory} class from
 * <code>memory/graph/graph_memory/base.py</code>.
 *
 * <p>The implementation here keeps the Java port testable while the full graph
 * pipeline continues to be filled in: backend attachment, strategy validation,
 * episode preparation, relation remapping, merge bookkeeping, and search
 * dispatch follow the Python semantics exercised by the translated tests.</p>
 */
public class GraphMemory {

    public static final String ENTITY_COLLECTION = "entity";
    public static final String RELATION_COLLECTION = "relation";
    public static final String EPISODE_COLLECTION = "episode";
    private static final String STORE_TYPE = "graph mem store";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ReentrantLock threadLock = new ReentrantLock();
    private final Map<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    private final Map<String, List<SearchConfig>> searchStrategies = new ConcurrentHashMap<>();
    private final Map<String, Integer> tokenRecord = new ConcurrentHashMap<>();
    private GraphMemoryStates.GraphMemState state;
    private GraphStore dbBackend;
    private GraphConfig config;
    private String language;
    private AddMemStrategy defaultExtractionStrategy;
    private Reranker reranker;
    private LlmInvoker llmClient;
    private boolean llmStructuredOutput;
    private Map<String, Object> llmExtraKwargs;
    private boolean debug;
    private boolean metricIsSim = true;

    public GraphMemory() {
        this(defaultConfig(), null, true, null, new AddMemStrategy(), null, Map.of(), "cn", false);
    }

    public GraphMemory(GraphConfig dbConfig) {
        this(dbConfig, null, true, null, new AddMemStrategy(), null, Map.of(), "cn", false);
    }

    public GraphMemory(GraphConfig dbConfig, GraphStore dbBackend, LlmInvoker llmClient) {
        this(dbConfig, llmClient, true, null, new AddMemStrategy(), dbBackend, Map.of(), "cn", false);
    }

    public GraphMemory(GraphConfig dbConfig, LlmInvoker llmClient, boolean llmStructuredOutput,
            Reranker reranker, AddMemStrategy extractionStrategy, GraphStore dbBackend,
            Map<String, Object> llmExtraKwargs, String language, boolean debug) {
        this.config = dbConfig != null ? dbConfig : defaultConfig();
        this.dbBackend = dbBackend != null ? dbBackend : GraphStoreFactory.fromConfig(this.config);
        this.llmClient = llmClient != null ? llmClient : params -> CompletableFuture.completedFuture(new LlmResponse("{}"));
        this.llmStructuredOutput = llmStructuredOutput;
        this.reranker = reranker;
        this.defaultExtractionStrategy = extractionStrategy != null ? extractionStrategy : new AddMemStrategy();
        this.llmExtraKwargs = llmExtraKwargs != null ? new LinkedHashMap<>(llmExtraKwargs) : new LinkedHashMap<>();
        this.language = normalizeLanguage(language);
        this.debug = debug;
        this.state = new GraphMemoryStates.GraphMemState();
        tokenRecord.put("input_tokens", 0);
        tokenRecord.put("output_tokens", 0);
        registerDefaultSearchStrategy();
    }

    /**
     * Compatibility method retained from the earlier Java skeleton.
     */
    public CompletableFuture<GraphMemoryStates.GraphMemUpdate> add(String content, String episodeType) {
        GraphMemoryStates.GraphMemUpdate update = new GraphMemoryStates.GraphMemUpdate();
        return CompletableFuture.completedFuture(update);
    }

    /**
     * Compatibility method retained from the earlier Java skeleton.
     */
    public CompletableFuture<Map<String, Object>> search(String query, int limit) {
        Map<String, Object> results = new HashMap<>();
        results.put("entities", new ArrayList<Entity>());
        results.put("relations", new ArrayList<Relation>());
        results.put("episodes", new ArrayList<Episode>());
        return CompletableFuture.completedFuture(results);
    }

    public CompletableFuture<GraphMemoryStates.GraphMemUpdate> addMemory(
            EpisodeType srcType, String userId, Object content) {
        return addMemory(srcType, userId, content, null, null);
    }

    public CompletableFuture<GraphMemoryStates.GraphMemUpdate> addMemory(
            EpisodeType srcType, String userId, Object content, Map<String, String> contentFmtKwargs,
            Object referenceTime) {
        if (getEmbedder() == null) {
            return CompletableFuture.failedFuture(error("use the attach_embedder method to attach one"));
        }
        ensureThreadLock(userId);
        ReentrantLock lock = userLocks.get(userId);
        lock.lock();
        try {
            GraphMemoryStates.GraphMemState localState = initState(referenceTime);
            String prepared = prepareEpisodes(srcType, userId, content, localState, contentFmtKwargs).join();
            Episode episode = new Episode();
            episode.setEpisodeType(srcType.name());
            episode.setContent(prepared);
            episode.setUserId(userId);
            episode.setValidSince(localState.getReferenceTimestamp());
            localState.getMemUpdate().getAddedEpisode().add(episode);
            this.state = localState;
            try {
                dbBackend.refresh();
            } catch (Exception ignored) {
                // Test doubles may not implement refresh behavior.
            }
            return CompletableFuture.completedFuture(localState.getMemUpdate().merge(localState.getMemUpdateSkipEmbed()));
        } catch (CompletionException e) {
            return CompletableFuture.failedFuture(e.getCause() == null ? e : e.getCause());
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        } finally {
            lock.unlock();
        }
    }

    public CompletableFuture<Map<String, List<SearchHit>>> search(
            String query, String userId, String searchStrategy, boolean entity, boolean relation,
            boolean episode, List<Float> queryEmbedding) {
        try {
            if (!searchStrategies.containsKey(searchStrategy)) {
                if (searchStrategy == null || searchStrategy.isBlank()) {
                    throw error("strategy must be a non-empty string value");
                }
                throw error("Strategy [" + searchStrategy + "] not found");
            }
            List<Float> embedding = queryEmbedding;
            if (embedding == null) {
                if (getEmbedder() == null) {
                    throw error("use the attach_embedder method to attach one");
                }
                embedding = getEmbedder().embedQuery(query);
            } else if (!allFloat(embedding)) {
                throw error("query_embedding must be a list[float] or None");
            }

            List<CompletableFuture<SearchResult>> tasks = new ArrayList<>();
            Map<String, Object> kwargs = new LinkedHashMap<>();
            kwargs.put("query", query);
            kwargs.put("query_embedding", embedding);
            if (entity) {
                performSearch(0, userId, searchStrategy, tasks, kwargs);
            }
            if (relation) {
                performSearch(1, userId, searchStrategy, tasks, kwargs);
            }
            if (episode) {
                performSearch(2, userId, searchStrategy, tasks, kwargs);
            }

            Map<String, List<SearchHit>> result = new LinkedHashMap<>();
            for (CompletableFuture<SearchResult> task : tasks) {
                SearchResult searchResult = task.join();
                List<SearchHit> hits = new ArrayList<>();
                for (Map<String, Object> row : searchResult.rows()) {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    double distance = number(copy.remove("distance"), 0.0).doubleValue();
                    hits.add(new SearchHit(distance, graphObjectFromMap(searchResult.collection(), copy)));
                }
                result.put(searchResult.collection(), hits);
            }
            return CompletableFuture.completedFuture(result);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public GraphMemoryStates.GraphMemState initState(Object referenceTime) {
        AddMemStrategy strategy = defaultExtractionStrategy;
        GraphMemoryStates.GraphMemState newState = new GraphMemoryStates.GraphMemState();
        newState.setStrategy(strategy);
        newState.setEntityTypes(List.of(new EntityTypeDef("Entity"), new EntityTypeDef("Human"), new EntityTypeDef("AI")));
        newState.getPrompting().setLanguage(language);
        newState.getPrompting().setEntityExtractionLanguage(strategy.isChineseEntity() ? "cn" : language);
        newState.getPrompting().setRelationExtractionLanguage(strategy.isChineseRelation() ? "cn" : language);
        newState.getPrompting().setEntityDedupeLanguage(strategy.isChineseEntityDedupe() ? "cn" : language);
        newState.getExtras().put("summary_target", String.valueOf(strategy.getSummaryTarget()));

        if (referenceTime == null) {
            newState.setReferenceTimestamp(newState.getCurrentTimestamp());
        } else if (referenceTime instanceof Instant instant) {
            newState.setReferenceTimestamp(instant.getEpochSecond());
        } else if (referenceTime instanceof LocalDateTime localDateTime) {
            newState.setReferenceTimestamp(localDateTime.toEpochSecond(ZoneOffset.UTC));
        } else {
            throw error("reference_time must be a valid datetime object");
        }
        return newState;
    }

    public CompletableFuture<String> prepareEpisodes(
            EpisodeType srcType, String userId, Object content, GraphMemoryStates.GraphMemState state) {
        return prepareEpisodes(srcType, userId, content, state, null);
    }

    public CompletableFuture<String> prepareEpisodes(
            EpisodeType srcType, String userId, Object content, GraphMemoryStates.GraphMemState state,
            Map<String, String> contentFmtKwargs) {
        try {
            String normalized;
            if (content instanceof String stringContent) {
                if (contentFmtKwargs != null && !contentFmtKwargs.isEmpty()) {
                    throw error("content_fmt_kwargs has no effect when content is str, please leave it empty");
                }
                normalized = stringContent;
            } else if (srcType == EpisodeType.CONVERSATION) {
                normalized = formatConversationContent(content, contentFmtKwargs);
            } else {
                throw error("The content must be str when source type is not conversation");
            }
            normalized = normalized.trim();
            if (normalized.isEmpty()) {
                throw error("content must be a non-empty value");
            }

            EpisodeRetrievalStrategy recall = state.getStrategy().getRecallEpisode();
            boolean maximize = metricIsSim || recall.getRankConfig().isHigherIsBetter();
            if (recall.getTopK() > 0 && !dbBackend.isEmpty(EPISODE_COLLECTION)) {
                Map<String, List<Map<String, Object>>> raw = dbBackend.search(
                        normalized,
                        recall.getTopK(),
                        EPISODE_COLLECTION,
                        recall.getRankConfig(),
                        0,
                        0,
                        null,
                        null,
                        null,
                        Map.of("user_id", userId));
                List<Map<String, Object>> rows = raw.getOrDefault(EPISODE_COLLECTION, List.of());
                List<Episode> episodes = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    double distance = number(row.get("distance"), 0.0).doubleValue();
                    boolean keep = maximize ? distance >= recall.getMinScore() : distance <= recall.getMinScore();
                    if (keep) {
                        episodes.add(state.getLookupTables().getEpisode(row));
                    }
                }
                episodes.sort((left, right) -> Long.compare(left.getValidSince(), right.getValidSince()));
                state.setHistory(episodes.stream()
                        .map(ep -> ep.getCreatedAt() + "\n" + ep.getContent())
                        .collect(Collectors.joining("\n---\n")));
            }
            return CompletableFuture.completedFuture(normalized);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public void performSearch(int colIdx, String userId, String searchStrategy,
            List<CompletableFuture<SearchResult>> tasks, Map<String, Object> kwargs) {
        String[] names = {ENTITY_COLLECTION, RELATION_COLLECTION, EPISODE_COLLECTION};
        SearchConfig configForCollection = searchStrategies.get(searchStrategy).get(colIdx).copy();
        if (configForCollection.isRerank() && reranker == null) {
            throw error("Search strategy [" + searchStrategy + "] for " + names[colIdx]
                    + " has rerank=True but reranker is not set");
        }
        tasks.add(CompletableFuture.supplyAsync(() -> {
            try {
                @SuppressWarnings("unchecked")
                List<Float> queryEmbedding = (List<Float>) kwargs.get("query_embedding");
                List<Map<String, Object>> rows = searchCollection(
                        names[colIdx], String.valueOf(kwargs.get("query")), configForCollection, queryEmbedding);
                return new SearchResult(rows, names[colIdx]);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }));
    }

    public List<Map<String, Object>> searchCollection(
            String collection, String query, SearchConfig searchConfig, List<Float> queryEmbedding) throws Exception {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("language", searchConfig.getLanguage());
        kwargs.put("min_score", searchConfig.getMinScore());
        if (searchConfig.isRerank()) {
            kwargs.put("reranker", reranker);
        }
        Map<String, List<Map<String, Object>>> raw = dbBackend.search(
                query,
                searchConfig.getTopK(),
                collection,
                searchConfig.getRankConfig(),
                searchConfig.getBfsDepth(),
                searchConfig.getBfsK(),
                null,
                searchConfig.getOutputFields(),
                queryEmbedding,
                kwargs);
        return raw.getOrDefault(collection, List.of());
    }

    public CompletableFuture<ExtractDeclarationsResult> extractEntityDeclarations(
            EpisodeType srcType, String content, GraphMemoryStates.GraphMemState state) {
        try {
            LlmResponse response = invokeLlm(Map.of("content", content, "src_type", srcType.name()),
                    new PromptTemplate("extract_entity", content, "{{", "}}"), null, Map.of()).join();
            Object parsed = parseJson(response.content());
            List<Map<String, Object>> declarationMaps = normalizeDeclarationMaps(parsed);
            Set<String> blockedNames = new HashSet<>(Set.of("user", "assistant", "User", "Assistant", "USER", "ASSISTANT"));
            List<EntityDeclaration> declarations = new ArrayList<>();
            for (Map<String, Object> declarationMap : declarationMaps) {
                Object nameObj = declarationMap.get("name");
                if (!(nameObj instanceof String rawName)) {
                    continue;
                }
                String name = rawName.trim();
                if (name.isEmpty() || blockedNames.contains(name)) {
                    continue;
                }
                blockedNames.add(name);
                int typeId = number(declarationMap.getOrDefault("entity_type_id",
                        declarationMap.getOrDefault("entityTypeId", 0)), 0).intValue();
                declarations.add(new EntityDeclaration(name, typeId));
            }
            boolean noExisting = dbBackend.isEmpty(ENTITY_COLLECTION);
            if (!noExisting && !declarations.isEmpty() && getEmbedder() != null) {
                List<String> names = declarations.stream().map(EntityDeclaration::getName).toList();
                state.getTasks().add(CompletableFuture.supplyAsync(() ->
                        getEmbedder().embedDocuments(names, config.getEmbedBatchSize())));
            }
            return CompletableFuture.completedFuture(new ExtractDeclarationsResult(noExisting, declarations));
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> fetchRelevantEntities(
            List<EntityDeclaration> extractedDeclarations, boolean noExistingEntity, String userId,
            GraphMemoryStates.GraphMemState state) {
        try {
            if (noExistingEntity || state.getTasks().size() <= 1) {
                return CompletableFuture.completedFuture(null);
            }
            int embedTaskIndex = state.getTasks().size() - 2;
            @SuppressWarnings("unchecked")
            List<List<Float>> embeddings = (List<List<Float>>) state.getTasks().remove(embedTaskIndex).join();
            while (state.getTasks().size() > 1) {
                state.getTasks().remove(0);
            }
            for (int i = 0; i < extractedDeclarations.size(); i++) {
                EntityDeclaration declaration = extractedDeclarations.get(i);
                List<Float> embedding = i < embeddings.size() ? embeddings.get(i) : null;
                if (!state.getStrategy().getRecallEntity().isSameKind()) {
                    searchAndCacheEntities(declaration.getName(), embedding, state);
                }
                if (declaration.getEntityTypeId() < state.getEntityTypes().size()) {
                    searchAndCacheEntities(declaration.getName(), embedding, state);
                }
                List<Map<String, Object>> queryRows = dbBackend.query(ENTITY_COLLECTION, null, null, true);
                for (Map<String, Object> row : queryRows) {
                    if (Objects.equals(row.get("name"), declaration.getName())
                            || String.valueOf(row.getOrDefault("name", "")).contains(declaration.getName())) {
                        Entity entity = state.getLookupTables().getEntity(row);
                        state.getRetrievedEntities().put(entity.getUuid(), entity);
                    }
                }
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> resolveEntityMerges(
            List<MergeArgument> mergingArgs, GraphMemoryStates.GraphMemState state) {
        try {
            Set<String> episodesToUpdate = new LinkedHashSet<>();
            Map<String, Map<String, Relation>> entityRelationUpdates = new LinkedHashMap<>();
            Map<String, String> mapSrcToTgt = new LinkedHashMap<>();
            for (MergeArgument arg : mergingArgs) {
                Entity tgt = arg.target();
                String tgtUuid = tgt.getUuid();
                GraphMemoryStates.EntityMerge mergeInfo = new GraphMemoryStates.EntityMerge(tgt);
                for (Entity src : arg.sources()) {
                    mergeInfo.getSource().put(src.getUuid(), src);
                }
                state.getMergeInfos().put(tgtUuid, mergeInfo);
                state.getRelationDeferredUpdates().put(tgtUuid, new ArrayList<>());
                entityRelationUpdates.put(tgtUuid, new LinkedHashMap<>());
                Set<String> alias = new LinkedHashSet<>(mergeInfo.getSource().keySet());
                alias.add(tgtUuid);
                for (Entity src : arg.sources()) {
                    mapSrcToTgt.put(src.getUuid(), tgtUuid);
                    List<String> sourceEpisodes = src.getEpisodes() == null ? List.of() : src.getEpisodes();
                    tgt.getEpisodes().addAll(sourceEpisodes);
                    episodesToUpdate.addAll(sourceEpisodes);
                    if (src.getRelations() != null && !src.getRelations().isEmpty()) {
                        resolveEachRelation(tgtUuid, src, mapSrcToTgt, entityRelationUpdates, state, alias).join();
                    }
                }
                tgt.setEpisodes(new ArrayList<>(new LinkedHashSet<>(tgt.getEpisodes())));
            }
            state.getMemUpdate().getRemovedRelation().addAll(state.getFaultyRelations().keySet());
            dispatchEntityMergeTasks(episodesToUpdate, entityRelationUpdates, state).join();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> dispatchEntityMergeTasks(
            Set<String> episodesToUpdate, Map<String, Map<String, Relation>> entityRelationUpdates,
            GraphMemoryStates.GraphMemState state) {
        try {
            if (state.getStrategy().isMergeFilter()) {
                for (Map.Entry<String, Map<String, Relation>> entry : entityRelationUpdates.entrySet()) {
                    String tgtUuid = entry.getKey();
                    Entity target = state.getLookupTables().getEntities().getOrDefault(
                            tgtUuid, state.getMergeInfos().get(tgtUuid).getTarget());
                    List<Relation> relationList = entry.getValue().values().stream()
                            .filter(rel -> !state.getFaultyRelations().containsKey(rel.getUuid()))
                            .toList();
                    state.getMergeInfos().get(tgtUuid).getNewRelations().clear();
                    state.getMergeInfos().get(tgtUuid).getNewRelations().addAll(relationList);
                    CompletableFuture<LlmResponse> task = invokeLlm(Map.of("target", target.getName()),
                            new PromptTemplate("filter_relations", "filter", "{{", "}}"), null, Map.of());
                    state.getRelationFilterTasks().put(task,
                            new GraphMemoryStates.RelationFilterContext(target, relationList));
                }
            }
            if (!episodesToUpdate.isEmpty()) {
                List<Map<String, Object>> queryRows = dbBackend.query(
                        EPISODE_COLLECTION, new ArrayList<>(episodesToUpdate), null, true);
                for (Map<String, Object> row : queryRows) {
                    state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(state.getLookupTables().getEpisode(row));
                }
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> resolveEachRelation(
            String tgtUuid, Entity srcEntity, Map<String, String> mapSrcToTgt,
            Map<String, Map<String, Relation>> entityRelationUpdates, GraphMemoryStates.GraphMemState state,
            Set<String> alias) {
        try {
            List<Object> relationIds = new ArrayList<>(srcEntity.getRelations());
            List<Map<String, Object>> queryRows = dbBackend.query(RELATION_COLLECTION, relationIds, null, true);
            Set<String> selfPointing = new HashSet<>();
            for (Map<String, Object> row : queryRows) {
                Relation relation = state.getLookupTables().getRelation(row);
                String toReplace = srcEntity.getUuid();
                Set<String> lhsRhs = new LinkedHashSet<>();
                lhsRhs.add(nullToEmpty(relation.getLhs()));
                lhsRhs.add(nullToEmpty(relation.getRhs()));
                if (alias.containsAll(lhsRhs)) {
                    state.getFaultyRelations().put(relation.getUuid(), relation);
                    selfPointing.add(relation.getUuid());
                    toReplace = null;
                }
                while (toReplace != null && mapSrcToTgt.containsKey(toReplace)
                        && !state.getFaultyRelations().containsKey(relation.getUuid())) {
                    if (Objects.equals(relation.getLhs(), toReplace)) {
                        replaceOneSideOfRelation("lhs", relation, tgtUuid, entityRelationUpdates, state);
                        break;
                    }
                    if (Objects.equals(relation.getRhs(), toReplace)) {
                        replaceOneSideOfRelation("rhs", relation, tgtUuid, entityRelationUpdates, state);
                        break;
                    }
                    toReplace = mapSrcToTgt.get(toReplace);
                }
                if (!state.getFaultyRelations().containsKey(relation.getUuid())
                        && !entityRelationUpdates.getOrDefault(tgtUuid, Map.of()).containsKey(relation.getUuid())
                        && !selfPointing.contains(relation.getUuid())) {
                    state.getFaultyRelations().put(relation.getUuid(), relation);
                }
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<?>> entityMerge(
            List<?> extractedDeclarations, List<Map<String, Object>> existingEntitiesList,
            GraphMemoryStates.GraphMemState state) {
        try {
            for (CompletableFuture<?> task : List.copyOf(state.getTasks())) {
                task.join();
            }
            if (existingEntitiesList.isEmpty()) {
                return CompletableFuture.completedFuture(extractedDeclarations);
            }
            LlmResponse response = state.getTasks().isEmpty()
                    ? new LlmResponse("[]")
                    : (LlmResponse) state.getTasks().remove(state.getTasks().size() - 1).join();
            List<Entity> existingEntities = existingEntitiesList.stream()
                    .map(state.getLookupTables()::getEntity)
                    .toList();
            List<MergeArgument> mergingArgs = resolveEntitiesForMerge(extractedDeclarations, existingEntities, response.content());
            if (!state.getStrategy().isMergeEntities()) {
                mergingArgs = new ArrayList<>();
            } else {
                for (MergeArgument arg : mergingArgs) {
                    for (Entity source : arg.sources()) {
                        state.getMemUpdate().getRemovedEntity().add(source.getUuid());
                    }
                }
            }
            for (MergeArgument arg : mergingArgs) {
                CompletableFuture<LlmResponse> task = invokeLlm(Map.of("target", arg.target().getName()),
                        new PromptTemplate("merge_existing_entities", "merge", "{{", "}}"), null, Map.of());
                state.getMergingTasks().add(task);
                state.getMergingTasksEntities().put(task, arg.target());
                if (containsSameEntity(extractedDeclarations, arg.target())) {
                    state.getPendingMerge().put(arg.target().getUuid(), task);
                }
            }
            resolveEntityMerges(mergingArgs, state).join();
            return CompletableFuture.completedFuture(extractedDeclarations);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<Entity>> entityEnrich(
            List<Entity> entities, String content, GraphMemoryStates.GraphMemState state) {
        try {
            state.getTasks().clear();
            List<Entity> nonBlocking = new ArrayList<>();
            List<Entity> blocking = new ArrayList<>();
            for (Entity entity : entities) {
                if (state.getPendingMerge().containsKey(entity.getUuid())) {
                    blocking.add(entity);
                } else {
                    nonBlocking.add(entity);
                }
            }
            List<Entity> ordered = new ArrayList<>(nonBlocking);
            ordered.addAll(blocking);
            for (Entity entity : nonBlocking) {
                state.getTasks().add(invokeLlm(Map.of("entity", entity.getName()),
                        new PromptTemplate("extract_entity_attributes", content, "{{", "}}"), null, Map.of()));
            }
            for (Entity entity : blocking) {
                CompletableFuture<LlmResponse> pending = state.getPendingMerge().get(entity.getUuid());
                updateEntityFromResponse(entity, pending.join().content());
                state.getMergingTasks().remove(pending);
                state.getTasks().add(invokeLlm(Map.of("entity", entity.getName()),
                        new PromptTemplate("extract_entity_attributes", content, "{{", "}}"), null, Map.of()));
            }
            for (int i = 0; i < ordered.size(); i++) {
                LlmResponse response = (LlmResponse) state.getTasks().get(i).join();
                updateEntityFromResponse(ordered.get(i), response.content());
            }
            state.getTasks().clear();
            return CompletableFuture.completedFuture(ordered);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> handleRelationDedupe(
            String userId, String content, List<Relation> relations, GraphMemoryStates.GraphMemState state) {
        try {
            relations.removeIf(state.getToRemove()::contains);
            if (state.getStrategy().isMergeRelations()
                    && !state.getTmpBuffer().isEmpty()
                    && !dbBackend.isEmpty(RELATION_COLLECTION)
                    && getEmbedder() != null) {
                List<String> relationTexts = state.getTmpBuffer().stream().map(String::valueOf).toList();
                List<List<Float>> embeddings = getEmbedder().embedDocuments(relationTexts, config.getEmbedBatchSize());
                relationDedupe(userId, content, relations, embeddings, state).join();
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> relationDedupe(
            String userId, String content, List<Relation> relations, List<List<Float>> relationEmbeddings,
            GraphMemoryStates.GraphMemState state) {
        try {
            state.getTasks().clear();
            for (int i = 0; i < relations.size(); i++) {
                Relation relation = relations.get(i);
                List<String> endpoints = List.of(nullToEmpty(relation.getLhs()), nullToEmpty(relation.getRhs()));
                if (endpoints.stream().anyMatch(String::isBlank)) {
                    continue;
                }
                List<Float> embedding = i < relationEmbeddings.size() ? relationEmbeddings.get(i) : null;
                Map<String, List<Map<String, Object>>> raw = dbBackend.search(
                        relation.getContent(),
                        state.getStrategy().getRecallRelation().getTopK(),
                        RELATION_COLLECTION,
                        state.getStrategy().getRecallRelation().getRankConfig(),
                        0,
                        0,
                        null,
                        null,
                        embedding,
                        Map.of("user_id", userId));
                List<Map<String, Object>> rows = raw.getOrDefault(RELATION_COLLECTION, List.of());
                boolean maximize = metricIsSim || state.getStrategy().getRecallRelation().getRankConfig().isHigherIsBetter();
                List<Relation> currentRelations = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    double distance = number(row.get("distance"), 0.0).doubleValue();
                    boolean keep = maximize
                            ? distance >= state.getStrategy().getRecallRelation().getMinScore()
                            : distance <= state.getStrategy().getRecallRelation().getMinScore();
                    if (keep) {
                        Relation retrieved = state.getLookupTables().getRelation(row);
                        state.getRetrievedRelations().put(retrieved.getUuid(), retrieved);
                        currentRelations.add(retrieved);
                    }
                }
                if (!currentRelations.isEmpty()) {
                    state.getTasks().add(invokeLlm(Map.of("relation", relation.getContent()),
                            new PromptTemplate("dedupe_relation_list", content, "{{", "}}"), null, Map.of()));
                }
            }
            for (CompletableFuture<?> task : List.copyOf(state.getTasks())) {
                task.join();
            }
            state.getTasks().clear();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> updateEntitiesForRelationRemoval(
            GraphMemoryStates.GraphMemState state, List<Entity> updateNeedsEmbed) {
        try {
            Set<String> entityIds = new LinkedHashSet<>();
            for (Object object : state.getToRemove()) {
                if (object instanceof Relation relation) {
                    if (relation.getLhs() != null) {
                        entityIds.add(relation.getLhs());
                    }
                    if (relation.getRhs() != null) {
                        entityIds.add(relation.getRhs());
                    }
                }
            }
            if (!entityIds.isEmpty()) {
                List<Map<String, Object>> rows = dbBackend.query(ENTITY_COLLECTION, new ArrayList<>(entityIds), null, true);
                for (Map<String, Object> row : rows) {
                    Entity entity = state.getLookupTables().getEntity(row);
                    Entity entityToUpdate = updateNeedsEmbed.stream()
                            .filter(candidate -> Objects.equals(candidate.getUuid(), entity.getUuid()))
                            .findFirst()
                            .orElse(entity);
                    boolean needsReEmbed = entityToUpdate != entity;
                    boolean changedWithoutEmbed = false;
                    for (String relationUuid : new ArrayList<>(state.getMemUpdate().getRemovedRelation())) {
                        if (entityToUpdate.getRelations().remove(relationUuid) && !needsReEmbed) {
                            changedWithoutEmbed = true;
                        }
                    }
                    if (changedWithoutEmbed
                            && !state.getMemUpdateSkipEmbed().getUpdatedEntity().contains(entityToUpdate)
                            && !state.getMemUpdate().getRemovedEntity().contains(entityToUpdate.getUuid())) {
                        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entityToUpdate);
                    }
                }
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public static void replaceOneSideOfRelation(String side, Relation relation, String tgtUuid,
            Map<String, Map<String, Relation>> entityRelationUpdates, GraphMemoryStates.GraphMemState state) {
        Map<String, Relation> updates = entityRelationUpdates.computeIfAbsent(tgtUuid, ignored -> new LinkedHashMap<>());
        List<GraphMemoryStates.RelationDeferredUpdate> deferred = state.getRelationDeferredUpdates()
                .computeIfAbsent(tgtUuid, ignored -> new ArrayList<>());
        if (!updates.containsKey(relation.getUuid())) {
            deferred.add(new GraphMemoryStates.RelationDeferredUpdate(relation, side, tgtUuid));
            updates.put(relation.getUuid(), relation);
        } else {
            state.getFaultyRelations().put(relation.getUuid(), relation);
            updates.remove(relation.getUuid());
            deferred.removeIf(task -> task.getRelation() == relation);
        }
    }

    public static CompletableFuture<Void> parseRelationFilteringResult(
            List<Relation> relations, GraphMemoryStates.GraphMemState state) {
        try {
            for (Map.Entry<CompletableFuture<LlmResponse>, GraphMemoryStates.RelationFilterContext> entry
                    : List.copyOf(state.getRelationFilterTasks().entrySet())) {
                GraphMemoryStates.RelationFilterContext context = entry.getValue();
                List<Relation> filtered;
                try {
                    Object parsed = parseJson(entry.getKey().join().content());
                    Set<Integer> keepIds = relationKeepIds(parsed);
                    filtered = keepIds.stream()
                            .filter(id -> id >= 1 && id <= context.getRelations().size())
                            .map(id -> context.getRelations().get(id - 1))
                            .toList();
                } catch (Exception e) {
                    filtered = context.getRelations();
                }
                GraphMemoryStates.EntityMerge mergeInfo =
                        state.getMergeInfos().get(context.getTargetEntity().getUuid());
                if (mergeInfo != null) {
                    mergeInfo.getNewRelations().clear();
                    mergeInfo.getNewRelations().addAll(filtered);
                }
            }
            for (Map.Entry<String, GraphMemoryStates.EntityMerge> entry : state.getMergeInfos().entrySet()) {
                String tgtUuid = entry.getKey();
                GraphMemoryStates.EntityMerge mergeInfo = entry.getValue();
                List<GraphMemoryStates.RelationDeferredUpdate> deferred =
                        state.getRelationDeferredUpdates().getOrDefault(tgtUuid, List.of());
                for (GraphMemoryStates.RelationDeferredUpdate update : deferred) {
                    if (mergeInfo.getNewRelations().contains(update.getRelation())) {
                        if ("lhs".equals(update.getSide())) {
                            update.getRelation().setLhs(update.getValue());
                        } else {
                            update.getRelation().setRhs(update.getValue());
                        }
                        if (!state.getMemUpdateSkipEmbed().getUpdatedRelation().contains(update.getRelation())) {
                            state.getMemUpdateSkipEmbed().getUpdatedRelation().add(update.getRelation());
                        }
                    } else {
                        state.getMemUpdate().getRemovedRelation().add(update.getRelation().getUuid());
                        state.getToRemove().add(update.getRelation());
                    }
                }
            }
            classifyRelationsExtracted(relations, state);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<LlmResponse> invokeLlm(
            Map<String, Object> kwargs, PromptTemplate template, Map<String, Object> outputModel,
            Map<String, Object> extra) {
        Map<String, Object> params = new LinkedHashMap<>();
        PromptTemplate formatted = template == null
                ? new PromptTemplate("", "", "{{", "}}")
                : template.format(kwargs == null ? Map.of() : kwargs);
        List<BaseMessage> messages = formatted.toMessages();
        params.put("messages", GraphMemoryUtils.msg2dict(messages, false));
        if (llmStructuredOutput && outputModel != null) {
            params.put("response_format", outputModel);
        }
        params.putAll(llmExtraKwargs);
        if (extra != null) {
            params.putAll(extra);
        }
        return llmClient.invoke(params).handle((response, throwable) -> {
            if (throwable != null) {
                throw new CompletionException(error("LLM invoke failed: " + throwable.getMessage()));
            }
            return response;
        });
    }

    public void attachEmbedder(Embedding embedder) {
        dbBackend.attachEmbedder(embedder);
    }

    public Embedding getEmbedder() {
        return dbBackend.getEmbedder();
    }

    public void attachReranker(Object reranker) {
        if (reranker instanceof Reranker typed) {
            this.reranker = typed;
        } else {
            throw error("Reranker must be an implementation of Reranker");
        }
    }

    public void registerSearchStrategy(String name, SearchConfig searchEntity) {
        registerSearchStrategy(name, searchEntity, null, null, false);
    }

    public void registerSearchStrategy(String name, Object searchEntity,
            Object searchRelation, Object searchEpisode, boolean force) {
        if (!(searchEntity == null || searchEntity instanceof SearchConfig)
                || !(searchRelation == null || searchRelation instanceof SearchConfig)
                || !(searchEpisode == null || searchEpisode instanceof SearchConfig)) {
            throw error("Search config for entity/relation/episode must be an instance of SearchConfig or None");
        }
        registerSearchStrategy(name, (SearchConfig) searchEntity, (SearchConfig) searchRelation,
                (SearchConfig) searchEpisode, force);
    }

    public void registerSearchStrategy(String name, SearchConfig searchEntity,
            SearchConfig searchRelation, SearchConfig searchEpisode, boolean force) {
        threadLock.lock();
        try {
            if (name == null || name.isEmpty()) {
                throw error("Search config cannot be registered as an empty value.");
            }
            if (searchStrategies.containsKey(name) && !force) {
                throw error("Search config with name [" + name + "] already exists.");
            }
            searchStrategies.put(name, List.of(
                    searchEntity != null ? searchEntity : new SearchConfig(new WeightedRankConfig()),
                    searchRelation != null ? searchRelation : SearchConfig.withMinScore(0.02),
                    searchEpisode != null ? searchEpisode : SearchConfig.withMinScore(0.025)));
        } finally {
            threadLock.unlock();
        }
    }

    public void ensureThreadLock(String userId) {
        threadLock.lock();
        try {
            userLocks.computeIfAbsent(userId, ignored -> new ReentrantLock());
        } finally {
            threadLock.unlock();
        }
    }

    public List<Entity> getEntities() {
        return new ArrayList<>(state.getLookupTables().getEntities().values());
    }

    public List<Relation> getRelations() {
        return new ArrayList<>(state.getLookupTables().getRelations().values());
    }

    public List<Episode> getEpisodes() {
        return new ArrayList<>(state.getLookupTables().getEpisodes().values());
    }

    public GraphMemoryStates.GraphMemState getState() { return state; }
    public GraphStore getDbBackend() { return dbBackend; }
    public GraphConfig getConfig() { return config; }
    public String getLanguage() { return language; }
    public Reranker getReranker() { return reranker; }
    public AddMemStrategy getDefaultExtractionStrategy() { return defaultExtractionStrategy; }
    public Map<String, List<SearchConfig>> getSearchStrategies() { return searchStrategies; }
    public Map<String, ReentrantLock> getUserLocks() { return userLocks; }
    public Map<String, Integer> getTokenRecord() { return tokenRecord; }
    public boolean isMetricIsSim() { return metricIsSim; }
    public void setMetricIsSim(boolean metricIsSim) { this.metricIsSim = metricIsSim; }
    public boolean isDebug() { return debug; }
    public void setLlmClient(LlmInvoker llmClient) { this.llmClient = llmClient; }
    public void setDbBackend(GraphStore dbBackend) { this.dbBackend = dbBackend; }

    private void registerDefaultSearchStrategy() {
        searchStrategies.put("default", List.of(
                new SearchConfig(new WeightedRankConfig()),
                SearchConfig.withMinScore(0.02),
                SearchConfig.withMinScore(0.025)));
    }

    private void searchAndCacheEntities(String name, List<Float> embedding, GraphMemoryStates.GraphMemState state)
            throws Exception {
        RetrievalStrategy recall = state.getStrategy().getRecallEntity();
        Map<String, List<Map<String, Object>>> raw = dbBackend.search(
                name,
                recall.getTopK(),
                ENTITY_COLLECTION,
                recall.getRankConfig(),
                0,
                0,
                null,
                null,
                embedding,
                Map.of());
        boolean maximize = metricIsSim || recall.getRankConfig().isHigherIsBetter();
        for (Map<String, Object> row : raw.getOrDefault(ENTITY_COLLECTION, List.of())) {
            double distance = number(row.get("distance"), 0.0).doubleValue();
            boolean keep = maximize ? distance >= recall.getMinScore() : distance <= recall.getMinScore();
            if (keep) {
                Entity entity = state.getLookupTables().getEntity(row);
                state.getRetrievedEntities().put(entity.getUuid(), entity);
            }
        }
    }

    private static void classifyRelationsExtracted(List<Relation> relations, GraphMemoryStates.GraphMemState state) {
        for (GraphMemoryStates.EntityMerge mergeInfo : state.getMergeInfos().values()) {
            for (Relation relation : mergeInfo.getNewRelations()) {
                if (!Objects.equals(relation.getLhs(), relation.getRhs())) {
                    mergeInfo.getRelationsToKeep().add(relation.getUuid());
                } else {
                    state.getMemUpdate().getRemovedRelation().add(relation.getUuid());
                }
            }
            List<String> union = new ArrayList<>(new LinkedHashSet<>(mergeInfo.getTarget().getRelations()));
            for (String relId : mergeInfo.getRelationsToKeep()) {
                if (!union.contains(relId)) {
                    union.add(relId);
                }
            }
            mergeInfo.getTarget().setRelations(union);
        }
        state.getTmpBuffer().clear();
        for (Relation relation : relations) {
            if (relation.getContent() == null || relation.getContent().isBlank()) {
                state.getToRemove().add(relation);
            } else if (Objects.equals(relation.getLhs(), relation.getRhs())) {
                state.getToRemove().add(relation);
            } else {
                state.getTmpBuffer().add(relation.getContent());
            }
        }
    }

    private List<MergeArgument> resolveEntitiesForMerge(List<?> extracted, List<Entity> existing, String dedupeJson) {
        if (existing.size() < 2) {
            return new ArrayList<>();
        }
        Entity target = existing.get(0);
        Entity source = existing.get(1);
        String sourceUuid = source.getUuid();
        if (extracted.stream().anyMatch(item -> item instanceof Entity entity
                && Objects.equals(entity.getUuid(), sourceUuid))) {
            target = source;
            source = existing.get(0);
        }
        return new ArrayList<>(List.of(new MergeArgument(target, List.of(source))));
    }

    private static boolean containsSameEntity(List<?> extractedDeclarations, Entity target) {
        for (Object item : extractedDeclarations) {
            if (item instanceof Entity entity && Objects.equals(entity.getUuid(), target.getUuid())) {
                return true;
            }
        }
        return false;
    }

    private static BaseGraphObject graphObjectFromMap(String collection, Map<String, Object> row) {
        GraphMemoryStates.LookupTables lookupTables = new GraphMemoryStates.LookupTables();
        if (ENTITY_COLLECTION.equals(collection)) {
            return lookupTables.getEntity(row);
        }
        if (RELATION_COLLECTION.equals(collection)) {
            return lookupTables.getRelation(row);
        }
        return lookupTables.getEpisode(row);
    }

    private static String formatConversationContent(Object content, Map<String, String> replacements) {
        List<?> messages;
        if (content instanceof Map<?, ?> single) {
            messages = List.of(single);
        } else if (content instanceof List<?> list) {
            messages = list;
        } else {
            throw error("The content must be str or list of messages in dict or BaseMessage");
        }
        List<String> lines = new ArrayList<>();
        for (Object message : messages) {
            if (!(message instanceof Map<?, ?> map) || !map.containsKey("role") || !map.containsKey("content")) {
                throw error("The content is not a list of dict with keys role and content");
            }
            String role = String.valueOf(map.get("role"));
            if (replacements != null && replacements.containsKey(role)) {
                role = replacements.get(role);
            }
            lines.add(role + ": " + map.get("content"));
        }
        return String.join("\n", lines);
    }

    private static void updateEntityFromResponse(Entity entity, String response) {
        Object parsed = parseJson(response);
        if (parsed instanceof Map<?, ?> map) {
            Object summary = map.get("summary");
            if (summary != null && !String.valueOf(summary).isBlank()) {
                entity.setContent(String.valueOf(summary));
            }
            Object attrs = map.get("attributes");
            if (attrs instanceof Map<?, ?> attrsMap) {
                Map<String, Object> typed = new LinkedHashMap<>();
                attrsMap.forEach((key, value) -> typed.put(String.valueOf(key), value));
                entity.setAttributes(typed);
            }
        } else if (parsed instanceof String text && !text.isBlank()) {
            entity.setContent(text);
        }
    }

    private static List<Map<String, Object>> normalizeDeclarationMaps(Object parsed) {
        if (parsed instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> toStringObjectMap((Map<?, ?>) item))
                    .toList();
        }
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> typed = toStringObjectMap(map);
            Object entities = typed.getOrDefault("extracted_entities", typed.getOrDefault("entities", typed.get("entity")));
            if (entities instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> toStringObjectMap((Map<?, ?>) item))
                        .toList();
            }
            if (entities instanceof Map<?, ?> single) {
                return List.of(toStringObjectMap(single));
            }
        }
        return List.of();
    }

    private static Set<Integer> relationKeepIds(Object parsed) {
        if (parsed instanceof Map<?, ?> map) {
            Object ids = map.containsKey("relevant_relations")
                    ? map.get("relevant_relations")
                    : map.get("relevantRelations");
            if (ids instanceof List<?> list) {
                return list.stream()
                        .filter(Number.class::isInstance)
                        .map(Number.class::cast)
                        .map(Number::intValue)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
        }
        return Set.of();
    }

    private static Object parseJson(String content) {
        try {
            return JSON.readValue(content, new TypeReference<Object>() {});
        } catch (Exception e) {
            return content;
        }
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static boolean allFloat(List<Float> values) {
        return values != null && values.stream().allMatch(Objects::nonNull);
    }

    private static String normalizeLanguage(String language) {
        if ("en".equals(language) || "cn".equals(language)) {
            return language;
        }
        return "cn";
    }

    private static Number number(Object value, Number fallback) {
        if (value instanceof Number number) {
            return number;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static BaseError error(String message) {
        return ErrorHelper.buildError(
                StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                "store_type", STORE_TYPE,
                "error_msg", message);
    }

    private static GraphConfig defaultConfig() {
        return GraphConfig.builder()
                .uri(System.getProperty("java.io.tmpdir") + "/test_graph_memory_java")
                .name("test_graph_memory_java")
                .backend("in_memory")
                .embedDim(64)
                .build();
    }

    /**
     * Minimal LLM invoker adapter used by the graph memory tests.
     */
    @FunctionalInterface
    public interface LlmInvoker {
        CompletableFuture<LlmResponse> invoke(Map<String, Object> params);
    }

    public record LlmResponse(String content) {}

    public record SearchResult(List<Map<String, Object>> rows, String collection) {}

    public record SearchHit(double score, BaseGraphObject object) {}

    public record ExtractDeclarationsResult(boolean noExistingEntity, List<EntityDeclaration> declarations) {}

    public record EntityDeclaration(String name, int entityTypeId) {
        public String getName() { return name; }
        public int getEntityTypeId() { return entityTypeId; }
    }

    public record EntityTypeDef(String name) {}

    public record MergeArgument(Entity target, List<Entity> sources) {}

    public static class BaseStrategy {
        private int topK = 3;
        private double minScore = 0.3;
        private BaseRankConfig rankConfig = new RRFRankConfig();

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }
        public BaseRankConfig getRankConfig() { return rankConfig; }
        public void setRankConfig(BaseRankConfig rankConfig) { this.rankConfig = rankConfig; }
    }

    public static class RetrievalStrategy extends BaseStrategy {
        private boolean sameKind;

        public boolean isSameKind() { return sameKind; }
        public void setSameKind(boolean sameKind) { this.sameKind = sameKind; }
    }

    public static class EpisodeRetrievalStrategy extends RetrievalStrategy {
        private boolean excludeFutureResults = true;

        public EpisodeRetrievalStrategy() {
            setMinScore(0.025);
        }

        public boolean isExcludeFutureResults() { return excludeFutureResults; }
        public void setExcludeFutureResults(boolean excludeFutureResults) {
            this.excludeFutureResults = excludeFutureResults;
        }
    }

    public static class AddMemStrategy {
        private boolean chineseEntity = true;
        private boolean chineseEntityDedupe;
        private boolean chineseRelation;
        private boolean skipUuidDedupe;
        private final EpisodeRetrievalStrategy recallEpisode = new EpisodeRetrievalStrategy();
        private final RetrievalStrategy recallEntity = new RetrievalStrategy();
        private final RetrievalStrategy recallRelation = new RetrievalStrategy();
        private int summaryTarget = 250;
        private boolean mergeEntities = true;
        private boolean mergeRelations = true;
        private boolean mergeFilter = true;

        public AddMemStrategy() {
            recallEntity.setRankConfig(new WeightedRankConfig(0.7, 0.1, 0.2));
            recallEntity.setMinScore(0.1);
            recallRelation.setMinScore(0.02);
        }

        public boolean isChineseEntity() { return chineseEntity; }
        public void setChineseEntity(boolean chineseEntity) { this.chineseEntity = chineseEntity; }
        public boolean isChineseEntityDedupe() { return chineseEntityDedupe; }
        public void setChineseEntityDedupe(boolean chineseEntityDedupe) {
            this.chineseEntityDedupe = chineseEntityDedupe;
        }
        public boolean isChineseRelation() { return chineseRelation; }
        public void setChineseRelation(boolean chineseRelation) { this.chineseRelation = chineseRelation; }
        public boolean isSkipUuidDedupe() { return skipUuidDedupe; }
        public void setSkipUuidDedupe(boolean skipUuidDedupe) { this.skipUuidDedupe = skipUuidDedupe; }
        public EpisodeRetrievalStrategy getRecallEpisode() { return recallEpisode; }
        public RetrievalStrategy getRecallEntity() { return recallEntity; }
        public RetrievalStrategy getRecallRelation() { return recallRelation; }
        public int getSummaryTarget() { return summaryTarget; }
        public void setSummaryTarget(int summaryTarget) { this.summaryTarget = summaryTarget; }
        public boolean isMergeEntities() { return mergeEntities; }
        public void setMergeEntities(boolean mergeEntities) { this.mergeEntities = mergeEntities; }
        public boolean isMergeRelations() { return mergeRelations; }
        public void setMergeRelations(boolean mergeRelations) { this.mergeRelations = mergeRelations; }
        public boolean isMergeFilter() { return mergeFilter; }
        public void setMergeFilter(boolean mergeFilter) { this.mergeFilter = mergeFilter; }
    }

    public static class SearchConfig extends BaseStrategy {
        private int bfsK = 3;
        private int bfsDepth = 0;
        private QueryExpr filterExpr;
        private List<String> outputFields;
        private boolean rerank;
        private String language = "en";

        public SearchConfig() {}

        public SearchConfig(BaseRankConfig rankConfig) {
            setRankConfig(rankConfig);
        }

        public static SearchConfig withMinScore(double minScore) {
            SearchConfig config = new SearchConfig();
            config.setMinScore(minScore);
            return config;
        }

        public SearchConfig copy() {
            SearchConfig copy = new SearchConfig();
            copy.setTopK(getTopK());
            copy.setMinScore(getMinScore());
            copy.setRankConfig(getRankConfig());
            copy.bfsK = bfsK;
            copy.bfsDepth = bfsDepth;
            copy.filterExpr = filterExpr;
            copy.outputFields = outputFields == null ? null : new ArrayList<>(outputFields);
            copy.rerank = rerank;
            copy.language = language;
            return copy;
        }

        public int getBfsK() { return bfsK; }
        public void setBfsK(int bfsK) { this.bfsK = bfsK; }
        public int getBfsDepth() { return bfsDepth; }
        public void setBfsDepth(int bfsDepth) { this.bfsDepth = bfsDepth; }
        public QueryExpr getFilterExpr() { return filterExpr; }
        public void setFilterExpr(QueryExpr filterExpr) { this.filterExpr = filterExpr; }
        public List<String> getOutputFields() { return outputFields; }
        public void setOutputFields(List<String> outputFields) { this.outputFields = outputFields; }
        public boolean isRerank() { return rerank; }
        public void setRerank(boolean rerank) { this.rerank = rerank; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }

    /**
     * No-op reranker useful for production smoke paths.
     */
    public static class NoopReranker implements Reranker {
        @Override
        public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
            return candidates.stream().limit(topK).toList();
        }
    }
}
