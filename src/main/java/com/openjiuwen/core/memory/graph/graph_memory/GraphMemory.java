/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.base_reranker.Reranker;
import com.openjiuwen.core.foundation.store.graph.BaseGraphObject;
import com.openjiuwen.core.foundation.store.graph.BaseRankConfig;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphStoreFactory;
import com.openjiuwen.core.foundation.store.graph.GraphStoreUtils;
import com.openjiuwen.core.foundation.store.graph.RRFRankConfig;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.foundation.store.graph.WeightedRankConfig;
import com.openjiuwen.core.foundation.store.query.QueryExpr;
import com.openjiuwen.core.memory.config.AddMemStrategy;
import com.openjiuwen.core.memory.config.EpisodeRetrievalStrategy;
import com.openjiuwen.core.memory.config.EpisodeType;
import com.openjiuwen.core.memory.config.RetrievalStrategy;
import com.openjiuwen.core.memory.config.SearchConfig;
import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.ExtractionModels;
import com.openjiuwen.core.memory.graph.extraction.ParseResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Graph memory store and retrieval.
 *
 * <p>Mirrors Python's {@code GraphMemory} in
 * {@code openjiuwen/core/memory/graph/graph_memory/base.py}.</p>
 */
public class GraphMemory {

    public static final String ENTITY_COLLECTION = "entity";
    public static final String RELATION_COLLECTION = "relation";
    public static final String EPISODE_COLLECTION = "episode";

    private static final String STORE_TYPE = "graph mem store";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final java.util.concurrent.Executor IO_EXECUTOR =
            OpenJiuwenExecutors.newCachedThreadPool("graph-memory-search-io", false);

    private final ReentrantLock threadLock = new ReentrantLock();
    private final Map<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    private final Map<String, List<SearchConfig>> searchStrategies = new ConcurrentHashMap<>();
    private final Map<String, Integer> tokenRecord = new ConcurrentHashMap<>();

    private GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
    private GraphStore dbBackend;
    private GraphConfig config;
    private String language;
    private AddMemStrategy defaultExtractionStrategy;
    private Reranker reranker;
    private LlmInvoker llmClient;
    private boolean llmStructuredOutput;
    private Map<String, Object> llmExtraKwargs;
    private boolean debug;
    private boolean metricIsSim;

    public GraphMemory() {
        this(defaultConfig(), null, true, null, new AddMemStrategy(), null, Map.of(), "cn", false);
    }

    public GraphMemory(GraphConfig dbConfig) {
        this(dbConfig, null, true, null, new AddMemStrategy(), null, Map.of(), "cn", false);
    }

    public GraphMemory(GraphConfig dbConfig, GraphStore dbBackend, LlmInvoker llmClient) {
        this(dbConfig, llmClient, true, null, new AddMemStrategy(), dbBackend, Map.of(), "cn", false);
    }

    public GraphMemory(GraphConfig dbConfig,
                       LlmInvoker llmClient,
                       boolean llmStructuredOutput,
                       Reranker reranker,
                       AddMemStrategy extractionStrategy,
                       GraphStore dbBackend,
                       Map<String, Object> llmExtraKwargs,
                       String language,
                       boolean debug) {
        this.config = dbConfig == null ? defaultConfig() : dbConfig;
        this.dbBackend = dbBackend == null ? createBackend(this.config) : dbBackend;
        this.llmClient = llmClient == null
                ? params -> CompletableFuture.completedFuture(new LlmResponse("{}"))
                : llmClient;
        this.llmStructuredOutput = llmStructuredOutput;
        this.reranker = reranker;
        this.defaultExtractionStrategy = extractionStrategy == null ? new AddMemStrategy() : extractionStrategy;
        this.llmExtraKwargs = llmExtraKwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(llmExtraKwargs);
        this.language = normalizeLanguage(language);
        this.debug = debug;
        this.metricIsSim = this.dbBackend.isReturnSimilarityScore();
        tokenRecord.put("input_tokens", 0);
        tokenRecord.put("output_tokens", 0);
        registerDefaultSearchStrategy();
    }

    public CompletableFuture<GraphMemoryStates.GraphMemUpdate> add(String content, String episodeType) {
        EpisodeType type = episodeType == null ? EpisodeType.CONVERSATION : EpisodeType.valueOf(episodeType);
        return addMemory(type, "default_user", content, null, null);
    }

    public CompletableFuture<GraphMemoryStates.GraphMemUpdate> addMemory(EpisodeType srcType,
                                                                         String userId,
                                                                         Object content) {
        return addMemory(srcType, userId, content, null, null);
    }

    public CompletableFuture<GraphMemoryStates.GraphMemUpdate> addMemory(EpisodeType srcType,
                                                                         String userId,
                                                                         Object content,
                                                                         Map<String, String> contentFmtKwargs,
                                                                         Object referenceTime) {
        try {
            GraphMemoryInputValidator.validateAddMemoryInput(32, srcType, userId, contentFmtKwargs);
            if (getEmbedder() == null) {
                return CompletableFuture.failedFuture(error("use the attach_embedder method to attach one"));
            }
            ensureThreadLock(userId);
            ReentrantLock lock = userLocks.get(userId);
            lock.lock();
            try {
                GraphMemoryStates.GraphMemState localState = initState(referenceTime);
                localState.setEpisodeType(srcType);
                String prepared = prepareEpisodes(srcType, userId, content, localState, contentFmtKwargs).join();
                Episode episode = GraphMemoryPostProcessor.createEpisode(dbBackend, userId, prepared, localState).join();
                dbBackend.addEpisode(List.of(episode), false, false, true).join();
                dbBackend.refresh(true).join();
                this.state = localState;
                return CompletableFuture.completedFuture(
                        localState.getMemUpdate().merge(localState.getMemUpdateSkipEmbed()));
            } finally {
                lock.unlock();
            }
        } catch (CompletionException exception) {
            return CompletableFuture.failedFuture(exception.getCause() == null ? exception : exception.getCause());
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Map<String, List<SearchHit>>> search(String query,
                                                                  String userId,
                                                                  String searchStrategy,
                                                                  boolean entity,
                                                                  boolean relation,
                                                                  boolean episode,
                                                                  List<Double> queryEmbedding) {
        return search(query, (Object) userId, searchStrategy, entity, relation, episode, queryEmbedding);
    }

    public CompletableFuture<Map<String, List<SearchHit>>> search(String query,
                                                                  Object userId,
                                                                  String searchStrategy,
                                                                  boolean entity,
                                                                  boolean relation,
                                                                  boolean episode,
                                                                  List<Double> queryEmbedding) {
        try {
            String strategyName = searchStrategy == null ? "default" : searchStrategy;
            if (!searchStrategies.containsKey(strategyName)) {
                if (strategyName.isBlank()) {
                    throw error("strategy must be a non-empty string value");
                }
                throw error("Strategy [" + strategyName + "] not found, please register with register_search_configs "
                        + "method or use \"default\".");
            }
            List<String> userIds = GraphMemoryInputValidator.validateSearchInput(
                    query, userId, List.of(entity, relation, episode));
            List<Double> embedding = queryEmbedding;
            if (embedding == null) {
                Embedding embedder = getEmbedder();
                if (embedder == null) {
                    throw error("use the attach_embedder method to attach one");
                }
                embedding = embedder.embedQuery(query).join();
            } else if (embedding.stream().anyMatch(Objects::isNull)) {
                throw error("query_embedding must be a list[float] or None");
            }

            List<CompletableFuture<SearchResult>> tasks = new ArrayList<>();
            Map<String, Object> kwargs = new LinkedHashMap<>();
            kwargs.put("query", query);
            kwargs.put("query_embedding", embedding);
            kwargs.put("user_ids", userIds);
            if (entity) {
                performSearch(0, strategyName, tasks, kwargs);
            }
            if (relation) {
                performSearch(1, strategyName, tasks, kwargs);
            }
            if (episode) {
                performSearch(2, strategyName, tasks, kwargs);
            }

            Map<String, List<SearchHit>> result = new LinkedHashMap<>();
            for (CompletableFuture<SearchResult> task : tasks) {
                SearchResult searchResult = task.join();
                List<SearchHit> hits = new ArrayList<>();
                for (Map<String, Object> row : searchResult.rows()) {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    double score = number(copy.remove("distance"), 0.0d).doubleValue();
                    hits.add(new SearchHit(score, graphObjectFromMap(searchResult.collection(), copy)));
                }
                result.put(searchResult.collection(), hits);
            }
            return CompletableFuture.completedFuture(result);
        } catch (CompletionException exception) {
            return CompletableFuture.failedFuture(exception.getCause() == null ? exception : exception.getCause());
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public GraphMemoryStates.GraphMemState initState(Object referenceTime) {
        GraphMemoryStates.GraphMemState newState = new GraphMemoryStates.GraphMemState();
        AddMemStrategy strategy = defaultExtractionStrategy;
        newState.setStrategy(strategy);
        newState.getPrompting().setLanguage(language);
        newState.getPrompting().setEntityExtractionLanguage(strategy.isChineseEntity() ? "cn" : language);
        newState.getPrompting().setRelationExtractionLanguage(strategy.isChineseRelation() ? "cn" : language);
        newState.getPrompting().setEntityDedupeLanguage(strategy.isChineseEntityDedupe() ? "cn" : language);
        newState.getExtras().put("summary_target", String.valueOf(strategy.getSummaryTarget()));
        newState.getEntityTypes().clear();
        newState.getEntityTypes().add(new EntityTypeDefinition.EntityDef());
        newState.getEntityTypes().add(new EntityTypeDefinition.HumanEntity());
        newState.getEntityTypes().add(new EntityTypeDefinition.AIEntity());

        if (referenceTime == null) {
            newState.setReferenceTimestamp(newState.getCurrentTimestamp());
        } else if (referenceTime instanceof Instant instant) {
            newState.setReferenceTimestamp(instant.getEpochSecond());
        } else if (referenceTime instanceof LocalDateTime localDateTime) {
            newState.setReferenceTimestamp(localDateTime.toEpochSecond(ZoneOffset.UTC));
        } else if (referenceTime instanceof java.time.OffsetDateTime offsetDateTime) {
            newState.setReferenceTimestamp(offsetDateTime.toInstant().getEpochSecond());
        } else if (referenceTime instanceof java.time.ZonedDateTime zonedDateTime) {
            newState.setReferenceTimestamp(zonedDateTime.toInstant().getEpochSecond());
        } else if (referenceTime instanceof Number number) {
            newState.setReferenceTimestamp(number.longValue());
        } else {
            throw error("reference_time must be a valid datetime object");
        }
        return newState;
    }

    public CompletableFuture<String> prepareEpisodes(EpisodeType srcType,
                                                     String userId,
                                                     Object content,
                                                     GraphMemoryStates.GraphMemState state) {
        return prepareEpisodes(srcType, userId, content, state, null);
    }

    public CompletableFuture<String> prepareEpisodes(EpisodeType srcType,
                                                     String userId,
                                                     Object content,
                                                     GraphMemoryStates.GraphMemState state,
                                                     Map<String, String> contentFmtKwargs) {
        try {
            String normalized;
            if (content instanceof String text) {
                if (contentFmtKwargs != null && !contentFmtKwargs.isEmpty()) {
                    throw error("content_fmt_kwargs has no effect when content is str, please leave it empty");
                }
                normalized = text;
            } else if (srcType == EpisodeType.CONVERSATION) {
                normalized = formatConversationContent(content, contentFmtKwargs);
            } else {
                throw error("The content must be str when source type is not conversation");
            }
            normalized = normalized.strip();
            if (normalized.isEmpty()) {
                throw error("content must be a non-empty value");
            }
            populateHistory(userId, normalized, state);
            return CompletableFuture.completedFuture(normalized);
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public void performSearch(int collectionIndex,
                              String searchStrategy,
                              List<CompletableFuture<SearchResult>> tasks,
                              Map<String, Object> kwargs) {
        String[] collections = {ENTITY_COLLECTION, RELATION_COLLECTION, EPISODE_COLLECTION};
        SearchConfig configForCollection = copySearchConfig(searchStrategies.get(searchStrategy).get(collectionIndex));
        if (configForCollection.isRerank() && reranker == null) {
            throw error("Search strategy [" + searchStrategy + "] for " + collections[collectionIndex]
                    + " has rerank=True but reranker is not set");
        }
        tasks.add(CompletableFuture.supplyAsync(() -> {
            @SuppressWarnings("unchecked")
            List<Double> embedding = (List<Double>) kwargs.get("query_embedding");
            List<Map<String, Object>> rows = searchCollection(
                    collections[collectionIndex],
                    String.valueOf(kwargs.get("query")),
                    configForCollection,
                    embedding);
            return new SearchResult(rows, collections[collectionIndex]);
        }, IO_EXECUTOR));
    }

    public List<Map<String, Object>> searchCollection(String collection,
                                                      String query,
                                                      SearchConfig searchConfig,
                                                      List<Double> queryEmbedding) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("language", searchConfig.getLanguage());
        kwargs.put("min_score", searchConfig.getMinScore());
        return dbBackend.search(
                query,
                searchConfig.getTopK(),
                collection,
                searchConfig.getRankConfig(),
                searchConfig.isRerank() ? reranker : null,
                searchConfig.getBfsDepth(),
                searchConfig.getBfsK(),
                searchConfig.getFilterExpr(),
                searchConfig.getOutputFields(),
                queryEmbedding,
                kwargs
        ).join().getOrDefault(collection, List.of());
    }

    public CompletableFuture<ExtractDeclarationsResult> extractEntityDeclarations(EpisodeType srcType,
                                                                                  String content,
                                                                                  GraphMemoryStates.GraphMemState state) {
        try {
            LlmResponse response = invokeLlm(
                    Map.of("content", content, "src_type", srcType.name()),
                    stringPrompt("extract_entity", content),
                    new ExtractionModels.EntityDeclaration().responseFormat(),
                    Map.of()).join();
            List<Map<String, Object>> declarationMaps = normalizeDeclarationMaps(parseJson(response.content()));
            Set<String> blockedNames = new LinkedHashSet<>(Set.of("user", "assistant", "User", "Assistant",
                    "USER", "ASSISTANT"));
            List<ExtractionModels.EntityDeclaration> declarations = new ArrayList<>();
            for (Map<String, Object> declarationMap : declarationMaps) {
                Object nameValue = declarationMap.get("name");
                if (!(nameValue instanceof String rawName)) {
                    continue;
                }
                String name = rawName.strip();
                if (name.isEmpty() || blockedNames.contains(name)) {
                    continue;
                }
                blockedNames.add(name);
                int typeId = number(declarationMap.getOrDefault(
                        "entity_type_id", declarationMap.getOrDefault("entityTypeId", 0)), 0).intValue();
                declarations.add(new ExtractionModels.EntityDeclaration(name, typeId));
            }
            boolean noExisting = dbBackend.isEmpty(ENTITY_COLLECTION);
            if (!noExisting && !declarations.isEmpty() && getEmbedder() != null) {
                List<String> names = declarations.stream().map(ExtractionModels.EntityDeclaration::getName).toList();
                state.getTasks().add(getEmbedder().embedDocuments(names, config.getEmbedBatchSize()));
            }
            return CompletableFuture.completedFuture(new ExtractDeclarationsResult(noExisting, declarations));
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Void> fetchRelevantEntities(List<ExtractionModels.EntityDeclaration> extractedDeclarations,
                                                         boolean noExistingEntity,
                                                         String userId,
                                                         GraphMemoryStates.GraphMemState state) {
        try {
            List<List<Double>> embeddings = consumeEmbeddingTask(state);
            if (noExistingEntity) {
                return CompletableFuture.completedFuture(null);
            }
            for (int index = 0; index < extractedDeclarations.size(); index++) {
                ExtractionModels.EntityDeclaration declaration = extractedDeclarations.get(index);
                List<Double> embedding = index < embeddings.size() ? embeddings.get(index) : null;
                searchAndCacheEntities(declaration.getName(), embedding, userId, state);
                List<Map<String, Object>> queryRows = dbBackend.query(ENTITY_COLLECTION, null, null, true).join();
                for (Map<String, Object> row : queryRows) {
                    String name = String.valueOf(row.getOrDefault("name", ""));
                    if (Objects.equals(name, declaration.getName()) || name.contains(declaration.getName())) {
                        Entity entity = state.getLookupTable().getEntity(row);
                        state.getRetrievedEntities().put(entity.getUuid(), entity);
                    }
                }
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Void> resolveEntityMerges(List<GraphMemoryLlmResponseParser.MergeArgument> mergingArgs,
                                                       GraphMemoryStates.GraphMemState state) {
        try {
            Set<String> episodesToUpdate = new LinkedHashSet<>();
            Map<String, Map<String, Relation>> entityRelationUpdates = new LinkedHashMap<>();
            Map<String, String> sourceToTarget = new LinkedHashMap<>();
            for (GraphMemoryLlmResponseParser.MergeArgument argument : mergingArgs) {
                Entity target = argument.target();
                String targetUuid = target.getUuid();
                GraphMemoryStates.EntityMerge mergeInfo = new GraphMemoryStates.EntityMerge(target);
                state.getMergeInfos().put(targetUuid, mergeInfo);
                state.getRelationDeferredUpdates().put(targetUuid, new ArrayList<>());
                entityRelationUpdates.put(targetUuid, new LinkedHashMap<>());
                Set<String> alias = new LinkedHashSet<>();
                alias.add(targetUuid);
                for (Entity source : argument.sources()) {
                    mergeInfo.getSource().put(source.getUuid(), source);
                    alias.add(source.getUuid());
                    sourceToTarget.put(source.getUuid(), targetUuid);
                    target.getEpisodes().addAll(source.getEpisodes());
                    episodesToUpdate.addAll(source.getEpisodes());
                    if (!source.getRelations().isEmpty()) {
                        resolveEachRelation(targetUuid, source, sourceToTarget, entityRelationUpdates, state, alias).join();
                    }
                }
                target.setEpisodes(new ArrayList<>(new LinkedHashSet<>(target.getEpisodes())));
            }
            state.getMemUpdate().getRemovedRelation().addAll(state.getFaultyRelations().keySet());
            dispatchEntityMergeTasks(episodesToUpdate, entityRelationUpdates, state).join();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Void> dispatchEntityMergeTasks(Set<String> episodesToUpdate,
                                                            Map<String, Map<String, Relation>> entityRelationUpdates,
                                                            GraphMemoryStates.GraphMemState state) {
        try {
            if (state.getStrategy().isMergeFilter()) {
                for (Map.Entry<String, Map<String, Relation>> entry : entityRelationUpdates.entrySet()) {
                    String targetUuid = entry.getKey();
                    GraphMemoryStates.EntityMerge mergeInfo = state.getMergeInfos().get(targetUuid);
                    if (mergeInfo == null) {
                        continue;
                    }
                    List<Relation> relationList = entry.getValue().values().stream()
                            .filter(relation -> !state.getFaultyRelations().containsKey(relation.getUuid()))
                            .toList();
                    mergeInfo.getNewRelations().clear();
                    mergeInfo.getNewRelations().addAll(relationList);
                    CompletableFuture<LlmResponse> task = invokeLlm(
                            Map.of("target", mergeInfo.getTarget().getName()),
                            stringPrompt("filter_relations", "filter"),
                            state.getPrompting().getSchemaRelationFilter(),
                            Map.of());
                    state.getRelationFilterTasks().put(task,
                            new GraphMemoryStates.RelationFilterTask(mergeInfo.getTarget(), relationList));
                }
            }
            if (!episodesToUpdate.isEmpty()) {
                List<Map<String, Object>> rows = dbBackend.query(
                        EPISODE_COLLECTION, new ArrayList<>(episodesToUpdate), null, true).join();
                for (Map<String, Object> row : rows) {
                    state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(state.getLookupTable().getEpisode(row));
                }
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Void> resolveEachRelation(String targetUuid,
                                                       Entity sourceEntity,
                                                       Map<String, String> sourceToTarget,
                                                       Map<String, Map<String, Relation>> entityRelationUpdates,
                                                       GraphMemoryStates.GraphMemState state,
                                                       Set<String> alias) {
        try {
            List<?> relationIds = new ArrayList<>(sourceEntity.getRelations());
            List<Map<String, Object>> rows = dbBackend.query(RELATION_COLLECTION, relationIds, null, true).join();
            Set<String> selfPointing = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                Relation relation = state.getLookupTable().getRelation(row);
                String toReplace = sourceEntity.getUuid();
                Set<String> lhsRhs = Set.of(stringRef(relation.getLhs()), stringRef(relation.getRhs()));
                if (alias.containsAll(lhsRhs)) {
                    state.getFaultyRelations().put(relation.getUuid(), relation);
                    selfPointing.add(relation.getUuid());
                    toReplace = null;
                }
                while (toReplace != null
                        && sourceToTarget.containsKey(toReplace)
                        && !state.getFaultyRelations().containsKey(relation.getUuid())) {
                    if (Objects.equals(stringRef(relation.getLhs()), toReplace)) {
                        replaceOneSideOfRelation("lhs", relation, targetUuid, entityRelationUpdates, state);
                        break;
                    }
                    if (Objects.equals(stringRef(relation.getRhs()), toReplace)) {
                        replaceOneSideOfRelation("rhs", relation, targetUuid, entityRelationUpdates, state);
                        break;
                    }
                    toReplace = sourceToTarget.get(toReplace);
                }
                if (!state.getFaultyRelations().containsKey(relation.getUuid())
                        && !entityRelationUpdates.getOrDefault(targetUuid, Map.of()).containsKey(relation.getUuid())
                        && !selfPointing.contains(relation.getUuid())) {
                    state.getFaultyRelations().put(relation.getUuid(), relation);
                }
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<List<?>> entityMerge(List<?> extractedDeclarations,
                                                  List<Map<String, Object>> existingEntitiesList,
                                                  GraphMemoryStates.GraphMemState state) {
        try {
            for (CompletableFuture<?> task : List.copyOf(state.getTasks())) {
                task.join();
            }
            if (existingEntitiesList.isEmpty()) {
                return CompletableFuture.completedFuture(extractedDeclarations);
            }
            Object response = state.getTasks().isEmpty()
                    ? "[]"
                    : state.getTasks().remove(state.getTasks().size() - 1).join();
            List<Entity> existingEntities = existingEntitiesList.stream()
                    .map(state.getLookupTable()::getEntity)
                    .toList();
            List<ExtractionModels.EntityDeclaration> candidates = extractedDeclarations.stream()
                    .filter(ExtractionModels.EntityDeclaration.class::isInstance)
                    .map(ExtractionModels.EntityDeclaration.class::cast)
                    .toList();
            List<Map<String, Object>> duplication = normalizeDeclarationMaps(parseJson(contentFrom(response)));
            GraphMemoryLlmResponseParser.ResolvedEntitiesResult resolved =
                    GraphMemoryLlmResponseParser.resolveEntities(candidates, existingEntities, duplication);
            if (state.getStrategy().isMergeEntities()) {
                state.getMemUpdate().getRemovedEntity().addAll(resolved.entityUuidsToRemove());
                resolveEntityMerges(resolved.mergingArgs(), state).join();
            }
            return CompletableFuture.completedFuture(resolved.entities());
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<List<Entity>> entityEnrich(List<Entity> entities,
                                                        String content,
                                                        GraphMemoryStates.GraphMemState state) {
        try {
            state.getTasks().clear();
            for (Entity entity : entities) {
                CompletableFuture<?> pending = state.getPendingMerge().remove(entity.getUuid());
                if (pending != null) {
                    GraphMemoryUtils.updateEntity(entity, contentFrom(pending.join()),
                            state.getPrompting().getSchemaEntityExtraction());
                    state.getMergingTasks().remove(pending);
                }
                state.getTasks().add(invokeLlm(
                        Map.of("entity", entity.getName()),
                        stringPrompt("extract_entity_attributes", content),
                        state.getPrompting().getSchemaEntityExtraction(),
                        Map.of()));
            }
            for (int index = 0; index < entities.size(); index++) {
                Object response = state.getTasks().get(index).join();
                GraphMemoryUtils.updateEntity(entities.get(index), contentFrom(response),
                        state.getPrompting().getSchemaEntityExtraction());
            }
            state.getTasks().clear();
            return CompletableFuture.completedFuture(entities);
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Void> handleRelationDedupe(String userId,
                                                        String content,
                                                        List<Relation> relations,
                                                        GraphMemoryStates.GraphMemState state) {
        try {
            relations.removeIf(state.getToRemove()::contains);
            if (state.getStrategy().isMergeRelations()
                    && !state.getTmpBuffer().isEmpty()
                    && !dbBackend.isEmpty(RELATION_COLLECTION)
                    && getEmbedder() != null) {
                List<String> relationTexts = state.getTmpBuffer().stream().map(String::valueOf).toList();
                List<List<Double>> embeddings = getEmbedder()
                        .embedDocuments(relationTexts, config.getEmbedBatchSize()).join();
                relationDedupe(userId, content, relations, embeddings, state).join();
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Void> relationDedupe(String userId,
                                                  String content,
                                                  List<Relation> relations,
                                                  List<List<Double>> relationEmbeddings,
                                                  GraphMemoryStates.GraphMemState state) {
        try {
            List<GraphMemoryPostProcessor.DedupeRelationTask> dedupeTasks = new ArrayList<>();
            for (int index = 0; index < relations.size(); index++) {
                Relation relation = relations.get(index);
                List<String> endpoints = List.of(stringRef(relation.getLhs()), stringRef(relation.getRhs()));
                if (endpoints.stream().anyMatch(String::isBlank)) {
                    continue;
                }
                List<Double> embedding = index < relationEmbeddings.size() ? relationEmbeddings.get(index) : null;
                RetrievalStrategy recall = state.getStrategy().getRecallRelation();
                List<Map<String, Object>> rows = dbBackend.search(
                        relation.getContent(),
                        recall.getTopK(),
                        RELATION_COLLECTION,
                        recall.getRankConfig(),
                        null,
                        0,
                        0,
                        null,
                        null,
                        embedding,
                        Map.of("user_id", userId)
                ).join().getOrDefault(RELATION_COLLECTION, List.of());
                boolean maximize = metricIsSim || recall.getRankConfig().isHigherIsBetter();
                List<Relation> currentRelations = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    double distance = number(row.get("distance"), 0.0d).doubleValue();
                    boolean keep = maximize ? distance >= recall.getMinScore() : distance <= recall.getMinScore();
                    if (keep) {
                        Relation retrieved = state.getLookupTable().getRelation(row);
                        state.getRetrievedRelations().put(retrieved.getUuid(), retrieved);
                        currentRelations.add(retrieved);
                    }
                }
                if (!currentRelations.isEmpty()) {
                    CompletableFuture<LlmResponse> future = invokeLlm(
                            Map.of("relation", relation.getContent()),
                            stringPrompt("dedupe_relation_list", content),
                            state.getPrompting().getSchemaRelationMerge(),
                            Map.of());
                    dedupeTasks.add(new GraphMemoryPostProcessor.DedupeRelationTask(relation, currentRelations, future));
                }
            }
            GraphMemoryPostProcessor.parseRelationUuidsToRemove(dedupeTasks, state).join();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<Void> updateEntitiesForRelationRemoval(GraphMemoryStates.GraphMemState state,
                                                                    List<Entity> updateNeedsEmbed) {
        try {
            Set<String> entityIds = new LinkedHashSet<>();
            for (Object object : state.getToRemove()) {
                if (object instanceof Relation relation) {
                    entityIds.add(stringRef(relation.getLhs()));
                    entityIds.add(stringRef(relation.getRhs()));
                }
            }
            entityIds.remove("");
            if (!entityIds.isEmpty()) {
                List<Map<String, Object>> rows = dbBackend.query(
                        ENTITY_COLLECTION, new ArrayList<>(entityIds), null, true).join();
                for (Map<String, Object> row : rows) {
                    Entity entity = state.getLookupTable().getEntity(row);
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
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public static void replaceOneSideOfRelation(String side,
                                                Relation relation,
                                                String targetUuid,
                                                Map<String, Map<String, Relation>> entityRelationUpdates,
                                                GraphMemoryStates.GraphMemState state) {
        Map<String, Relation> updates = entityRelationUpdates.computeIfAbsent(targetUuid, ignored -> new LinkedHashMap<>());
        List<GraphMemoryStates.RelationDeferredUpdate> deferred = state.getRelationDeferredUpdates()
                .computeIfAbsent(targetUuid, ignored -> new ArrayList<>());
        if (!updates.containsKey(relation.getUuid())) {
            deferred.add(new GraphMemoryStates.RelationDeferredUpdate(relation, side, targetUuid));
            updates.put(relation.getUuid(), relation);
            return;
        }
        state.getFaultyRelations().put(relation.getUuid(), relation);
        updates.remove(relation.getUuid());
        deferred.removeIf(update -> update.relation() == relation);
    }

    public static CompletableFuture<Void> parseRelationFilteringResult(List<Relation> relations,
                                                                       GraphMemoryStates.GraphMemState state) {
        try {
            for (Map.Entry<CompletableFuture<?>, GraphMemoryStates.RelationFilterTask> entry
                    : List.copyOf(state.getRelationFilterTasks().entrySet())) {
                GraphMemoryStates.RelationFilterTask context = entry.getValue();
                List<Relation> filtered;
                try {
                    Set<Integer> keepIds = relationKeepIds(parseJson(contentFrom(entry.getKey().join())));
                    filtered = keepIds.stream()
                            .filter(id -> id >= 1 && id <= context.relations().size())
                            .map(id -> context.relations().get(id - 1))
                            .toList();
                } catch (RuntimeException exception) {
                    filtered = context.relations();
                }
                GraphMemoryStates.EntityMerge mergeInfo = state.getMergeInfos().get(context.entity().getUuid());
                if (mergeInfo != null) {
                    mergeInfo.getNewRelations().clear();
                    mergeInfo.getNewRelations().addAll(filtered);
                }
            }

            for (Map.Entry<String, GraphMemoryStates.EntityMerge> entry : state.getMergeInfos().entrySet()) {
                String targetUuid = entry.getKey();
                GraphMemoryStates.EntityMerge mergeInfo = entry.getValue();
                List<GraphMemoryStates.RelationDeferredUpdate> deferred =
                        state.getRelationDeferredUpdates().getOrDefault(targetUuid, List.of());
                for (GraphMemoryStates.RelationDeferredUpdate update : deferred) {
                    if (mergeInfo.getNewRelations().contains(update.relation())) {
                        if ("lhs".equals(update.lhsUuid())) {
                            update.relation().setLhs(update.rhsUuid());
                        } else {
                            update.relation().setRhs(update.rhsUuid());
                        }
                        if (!state.getMemUpdateSkipEmbed().getUpdatedRelation().contains(update.relation())) {
                            state.getMemUpdateSkipEmbed().getUpdatedRelation().add(update.relation());
                        }
                    } else {
                        state.getMemUpdate().getRemovedRelation().add(update.relation().getUuid());
                        state.getToRemove().add(update.relation());
                    }
                }
            }
            GraphMemoryStates.classifyRelationsExtracted(relations, state);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<LlmResponse> invokeLlm(Map<String, Object> kwargs,
                                                    PromptTemplate template,
                                                    Map<String, Object> outputModel,
                                                    Map<String, Object> extra) {
        Map<String, Object> params = GraphMemoryUtils.assembleInvokeParams(
                kwargs == null ? Map.of() : kwargs,
                template == null ? PromptTemplate.builder().content("").build() : template,
                llmStructuredOutput ? outputModel : null);
        params.putAll(llmExtraKwargs);
        if (extra != null) {
            params.putAll(extra);
        }
        return llmClient.invoke(params).handle((response, throwable) -> {
            if (throwable != null) {
                throw new CompletionException(error("LLM invoke failed: " + throwable.getMessage()));
            }
            return response == null ? new LlmResponse("{}") : response;
        });
    }

    public void attachEmbedder(Embedding embedder) {
        dbBackend.attachEmbedder(embedder);
    }

    public Embedding getEmbedder() {
        return dbBackend.getEmbedder().orElse(null);
    }

    public void attachReranker(Object reranker) {
        if (reranker instanceof Reranker typed) {
            this.reranker = typed;
            return;
        }
        throw error("Reranker must be an implementation of Reranker, got "
                + (reranker == null ? "null" : reranker.getClass()) + " instead.");
    }

    public void registerSearchStrategy(String name, SearchConfig searchEntity) {
        registerSearchStrategy(name, searchEntity, null, null, false);
    }

    public void registerSearchStrategy(String name,
                                       Object searchEntity,
                                       Object searchRelation,
                                       Object searchEpisode,
                                       boolean force) {
        if (!(searchEntity == null || searchEntity instanceof SearchConfig)
                || !(searchRelation == null || searchRelation instanceof SearchConfig)
                || !(searchEpisode == null || searchEpisode instanceof SearchConfig)) {
            throw error("Search config for entity/relation/episode must be an instance of SearchConfig or None");
        }
        registerSearchStrategy(name, (SearchConfig) searchEntity, (SearchConfig) searchRelation,
                (SearchConfig) searchEpisode, force);
    }

    public void registerSearchStrategy(String name,
                                       SearchConfig searchEntity,
                                       SearchConfig searchRelation,
                                       SearchConfig searchEpisode,
                                       boolean force) {
        threadLock.lock();
        try {
            if (name == null || name.isBlank()) {
                throw error("Search config cannot be registered as an empty value.");
            }
            if (searchStrategies.containsKey(name) && !force) {
                throw error("Search config with name [" + name + "] already exists.");
            }
            searchStrategies.put(name, List.of(
                    searchEntity == null ? defaultSearchConfig(new WeightedRankConfig(), 0.3d) : searchEntity,
                    searchRelation == null ? defaultSearchConfig(new RRFRankConfig(), 0.02d) : searchRelation,
                    searchEpisode == null ? defaultSearchConfig(new RRFRankConfig(), 0.025d) : searchEpisode));
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
        return new ArrayList<>(state.getLookupTable().getEntities().values());
    }

    public List<Relation> getRelations() {
        return new ArrayList<>(state.getLookupTable().getRelations().values());
    }

    public List<Episode> getEpisodes() {
        return new ArrayList<>(state.getLookupTable().getEpisodes().values());
    }

    public GraphMemoryStates.GraphMemState getState() {
        return state;
    }

    public GraphStore getDbBackend() {
        return dbBackend;
    }

    public GraphConfig getConfig() {
        return config;
    }

    public String getLanguage() {
        return language;
    }

    public Reranker getReranker() {
        return reranker;
    }

    public AddMemStrategy getDefaultExtractionStrategy() {
        return defaultExtractionStrategy;
    }

    public Map<String, List<SearchConfig>> getSearchStrategies() {
        return searchStrategies;
    }

    public Map<String, ReentrantLock> getUserLocks() {
        return userLocks;
    }

    public Map<String, Integer> getTokenRecord() {
        return tokenRecord;
    }

    public boolean isMetricIsSim() {
        return metricIsSim;
    }

    public void setMetricIsSim(boolean metricIsSim) {
        this.metricIsSim = metricIsSim;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setLlmClient(LlmInvoker llmClient) {
        this.llmClient = llmClient == null
                ? params -> CompletableFuture.completedFuture(new LlmResponse("{}"))
                : llmClient;
    }

    public void setDbBackend(GraphStore dbBackend) {
        this.dbBackend = Objects.requireNonNull(dbBackend, "dbBackend");
        this.metricIsSim = dbBackend.isReturnSimilarityScore();
    }

    private void populateHistory(String userId, String normalized, GraphMemoryStates.GraphMemState state) {
        EpisodeRetrievalStrategy recall = state.getStrategy().getRecallEpisode();
        if (recall.getTopK() <= 0 || dbBackend.isEmpty(EPISODE_COLLECTION)) {
            return;
        }
        List<Map<String, Object>> rows = dbBackend.search(
                normalized,
                recall.getTopK(),
                EPISODE_COLLECTION,
                recall.getRankConfig(),
                null,
                0,
                0,
                null,
                null,
                null,
                Map.of("user_id", userId)
        ).join().getOrDefault(EPISODE_COLLECTION, List.of());
        boolean maximize = metricIsSim || recall.getRankConfig().isHigherIsBetter();
        List<Episode> episodes = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            double distance = number(row.get("distance"), 0.0d).doubleValue();
            boolean keep = maximize ? distance >= recall.getMinScore() : distance <= recall.getMinScore();
            if (keep) {
                episodes.add(state.getLookupTable().getEpisode(row));
            }
        }
        episodes.sort((left, right) -> Long.compare(left.getValidSince(), right.getValidSince()));
        state.setHistory(episodes.stream()
                .map(episode -> episode.getCreatedAt() + "\n" + episode.getContent())
                .collect(Collectors.joining("\n---\n")));
    }

    private void registerDefaultSearchStrategy() {
        searchStrategies.put("default", List.of(
                defaultSearchConfig(new WeightedRankConfig(), 0.3d),
                defaultSearchConfig(new RRFRankConfig(), 0.02d),
                defaultSearchConfig(new RRFRankConfig(), 0.025d)));
    }

    private void searchAndCacheEntities(String name,
                                        List<Double> embedding,
                                        String userId,
                                        GraphMemoryStates.GraphMemState state) {
        RetrievalStrategy recall = state.getStrategy().getRecallEntity();
        List<Map<String, Object>> rows = dbBackend.search(
                name,
                recall.getTopK(),
                ENTITY_COLLECTION,
                recall.getRankConfig(),
                null,
                0,
                0,
                null,
                null,
                embedding,
                Map.of("user_id", userId)
        ).join().getOrDefault(ENTITY_COLLECTION, List.of());
        boolean maximize = metricIsSim || recall.getRankConfig().isHigherIsBetter();
        for (Map<String, Object> row : rows) {
            double distance = number(row.get("distance"), 0.0d).doubleValue();
            boolean keep = maximize ? distance >= recall.getMinScore() : distance <= recall.getMinScore();
            if (keep) {
                Entity entity = state.getLookupTable().getEntity(row);
                state.getRetrievedEntities().put(entity.getUuid(), entity);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<List<Double>> consumeEmbeddingTask(GraphMemoryStates.GraphMemState state) {
        for (int index = 0; index < state.getTasks().size(); index++) {
            Object result = state.getTasks().get(index).join();
            if (result instanceof List<?> list && (list.isEmpty() || list.get(0) instanceof List<?>)) {
                state.getTasks().remove(index);
                return (List<List<Double>>) result;
            }
        }
        return List.of();
    }

    private static SearchConfig defaultSearchConfig(BaseRankConfig rankConfig, double minScore) {
        SearchConfig config = new SearchConfig();
        config.setRankConfig(rankConfig);
        config.setMinScore(minScore);
        return config;
    }

    private static SearchConfig copySearchConfig(SearchConfig source) {
        SearchConfig copy = new SearchConfig();
        copy.setTopK(source.getTopK());
        copy.setMinScore(source.getMinScore());
        copy.setRankConfig(source.getRankConfig());
        copy.setBfsK(source.getBfsK());
        copy.setBfsDepth(source.getBfsDepth());
        copy.setFilterExpr(source.getFilterExpr());
        copy.setOutputFields(source.getOutputFields() == null ? null : new ArrayList<>(source.getOutputFields()));
        copy.setRerank(source.isRerank());
        copy.setLanguage(source.getLanguage());
        return copy;
    }

    private static PromptTemplate stringPrompt(String name, String content) {
        return PromptTemplate.builder()
                .name(name)
                .content(List.of(UserMessage.builder().content(content == null ? "" : content).build()))
                .build();
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
        List<Map<String, Object>> messageMaps = GraphMemoryUtils.msgToDict(messages);
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> message : messageMaps) {
            if (!message.containsKey("role") || !message.containsKey("content")) {
                throw error("The content is not a list of dict with keys role and content");
            }
            String role = String.valueOf(message.get("role"));
            if (replacements != null && replacements.containsKey(role)) {
                role = replacements.get(role);
            }
            lines.add(role + ": " + message.get("content"));
        }
        return String.join("\n", lines);
    }

    private static List<Map<String, Object>> normalizeDeclarationMaps(Object parsed) {
        if (parsed instanceof Collection<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> stringObjectMap((Map<?, ?>) item))
                    .toList();
        }
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> typed = stringObjectMap(map);
            Object entities = typed.getOrDefault("extracted_entities",
                    typed.getOrDefault("entities", typed.get("entity")));
            if (entities instanceof Collection<?> collection) {
                return collection.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> stringObjectMap((Map<?, ?>) item))
                        .toList();
            }
            if (entities instanceof Map<?, ?> single) {
                return List.of(stringObjectMap(single));
            }
            return List.of(typed);
        }
        return List.of();
    }

    private static Set<Integer> relationKeepIds(Object parsed) {
        if (parsed instanceof Map<?, ?> map) {
            Object ids = map.containsKey("relevant_relations")
                    ? map.get("relevant_relations")
                    : map.get("relevantRelations");
            if (ids instanceof Collection<?> collection) {
                Set<Integer> result = new LinkedHashSet<>();
                for (Object id : collection) {
                    if (id instanceof Number number) {
                        result.add(number.intValue());
                    }
                }
                return result;
            }
        }
        return Set.of();
    }

    private static Object parseJson(String content) {
        try {
            return JSON.readValue(content, new TypeReference<Object>() {});
        } catch (Exception ignored) {
            return content;
        }
    }

    private static Object parseJson(Object content) {
        return content instanceof String text ? parseJson(text) : content;
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static String contentFrom(Object response) {
        if (response == null) {
            return "{}";
        }
        if (response instanceof LlmResponse llmResponse) {
            return llmResponse.content();
        }
        return String.valueOf(response);
    }

    private static String stringRef(Object value) {
        if (value instanceof BaseGraphObject graphObject) {
            return graphObject.getUuid();
        }
        return value == null ? "" : String.valueOf(value);
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

    private static String normalizeLanguage(String value) {
        return "en".equals(value) ? "en" : "cn";
    }

    private static BaseError error(String message) {
        return ErrorHelper.buildError(
                StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                "store_type", STORE_TYPE,
                "error_msg", message);
    }

    private static GraphConfig defaultConfig() {
        return GraphConfig.builder()
                .uri(System.getProperty("java.io.tmpdir") + "/openjiuwen_graph_memory")
                .name("openjiuwen_graph_memory")
                .backend("in_memory")
                .embedDim(64)
                .build();
    }

    private static GraphStore createBackend(GraphConfig config) {
        try {
            return GraphStoreFactory.fromConfig(config);
        } catch (RuntimeException exception) {
            return new InMemoryGraphStore(config);
        }
    }

    /**
     * <p>Mirrors Python's LLM invocation callable used by {@code GraphMemory} in
     * {@code openjiuwen/core/memory/graph/graph_memory/base.py}.</p>
     */
    @FunctionalInterface
    public interface LlmInvoker {
        CompletableFuture<LlmResponse> invoke(Map<String, Object> params);
    }

    /**
     * <p>Mirrors Python's LLM response object consumed by {@code GraphMemory._invoke_llm} in
     * {@code openjiuwen/core/memory/graph/graph_memory/base.py}.</p>
     */
    public record LlmResponse(String content) {
    }

    /**
     * <p>Mirrors Python's per-collection search result handled by {@code GraphMemory.search} in
     * {@code openjiuwen/core/memory/graph/graph_memory/base.py}.</p>
     */
    public record SearchResult(List<Map<String, Object>> rows, String collection) {
    }

    /**
     * <p>Mirrors Python's {@code (score, BaseGraphObject)} search tuple in
     * {@code openjiuwen/core/memory/graph/graph_memory/base.py}.</p>
     */
    public record SearchHit(double score, BaseGraphObject object) {
    }

    /**
     * <p>Mirrors Python's {@code _extract_entity_declarations} return tuple in
     * {@code openjiuwen/core/memory/graph/graph_memory/base.py}.</p>
     */
    public record ExtractDeclarationsResult(boolean noExistingEntity,
                                            List<ExtractionModels.EntityDeclaration> declarations) {
    }

    /**
     * No-op reranker useful for smoke paths.
     *
     * <p>Mirrors Python's optional reranker dependency used by {@code GraphMemory} in
     * {@code openjiuwen/core/memory/graph/graph_memory/base.py}.</p>
     */
    public static class NoopReranker extends Reranker {
        @Override
        public CompletableFuture<Map<String, Double>> rerank(String query,
                                                             List<Object> doc,
                                                             Object instruct,
                                                             Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(rerankSync(query, doc, instruct, kwargs));
        }

        @Override
        public Map<String, Double> rerankSync(String query,
                                              List<Object> doc,
                                              Object instruct,
                                              Map<String, Object> kwargs) {
            Map<String, Double> scores = new LinkedHashMap<>();
            for (Object item : doc) {
                scores.put(String.valueOf(item), 0.0d);
            }
            return scores;
        }
    }

    /**
     * <p>Mirrors Python's graph-store backend boundary used by {@code GraphMemory} in
     * {@code openjiuwen/core/memory/graph/graph_memory/base.py}.</p>
     */
    private static final class InMemoryGraphStore implements GraphStore {
        private final GraphConfig config;
        private final Map<String, Map<String, Map<String, Object>>> collections = new ConcurrentHashMap<>();
        private Embedding embedder;

        private InMemoryGraphStore(GraphConfig config) {
            this.config = config;
            this.embedder = config.getEmbeddingModel();
            collections.put(ENTITY_COLLECTION, new ConcurrentHashMap<>());
            collections.put(RELATION_COLLECTION, new ConcurrentHashMap<>());
            collections.put(EPISODE_COLLECTION, new ConcurrentHashMap<>());
        }

        @Override
        public GraphConfig getConfig() {
            return config;
        }

        @Override
        public Optional<Semaphore> getSemophore() {
            return Optional.empty();
        }

        @Override
        public Optional<Embedding> getEmbedder() {
            return Optional.ofNullable(embedder);
        }

        @Override
        public boolean isReturnSimilarityScore() {
            return true;
        }

        @Override
        public void rebuild() {
            collections.values().forEach(Map::clear);
        }

        @Override
        public CompletableFuture<Void> refresh(boolean skipCompact, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addData(String collection,
                                               Iterable<Map<String, Object>> data,
                                               boolean flush,
                                               boolean upsert,
                                               Map<String, Object> kwargs) {
            Map<String, Map<String, Object>> target = collection(collection);
            for (Map<String, Object> row : data) {
                Map<String, Object> copy = new LinkedHashMap<>(row);
                String uuid = String.valueOf(copy.getOrDefault("uuid", GraphStoreUtils.getUuid()));
                copy.put("uuid", uuid);
                target.put(uuid, copy);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {
            return addObjects(ENTITY_COLLECTION, entities);
        }

        @Override
        public CompletableFuture<Void> addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) {
            return addObjects(RELATION_COLLECTION, relations);
        }

        @Override
        public CompletableFuture<Void> addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) {
            return addObjects(EPISODE_COLLECTION, episodes);
        }

        @Override
        public boolean isEmpty(String collection) {
            return collection(collection).isEmpty();
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> query(String collection,
                                                                  List<?> ids,
                                                                  QueryExpr expr,
                                                                  boolean silenceErrors,
                                                                  Map<String, Object> kwargs) {
            Map<String, Map<String, Object>> source = collection(collection);
            if (ids == null) {
                List<Map<String, Object>> rows = source.values().stream()
                        .map(row -> new LinkedHashMap<String, Object>(row))
                        .collect(Collectors.toList());
                return CompletableFuture.completedFuture(rows);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object id : ids) {
                Map<String, Object> row = source.get(String.valueOf(id));
                if (row != null) {
                    rows.add(new LinkedHashMap<>(row));
                }
            }
            return CompletableFuture.completedFuture(rows);
        }

        @Override
        public CompletableFuture<Map<String, Object>> delete(String collection,
                                                             List<?> ids,
                                                             QueryExpr expr,
                                                             Map<String, Object> kwargs) {
            Map<String, Map<String, Object>> target = collection(collection);
            if (ids == null) {
                target.clear();
            } else {
                ids.forEach(id -> target.remove(String.valueOf(id)));
            }
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public CompletableFuture<Map<String, List<Map<String, Object>>>> search(String query,
                                                                                int k,
                                                                                String collection,
                                                                                BaseRankConfig rankerConfig,
                                                                                Reranker reranker,
                                                                                int bfsDepth,
                                                                                int bfsK,
                                                                                QueryExpr filterExpr,
                                                                                List<String> outputFields,
                                                                                List<Double> queryEmbedding,
                                                                                Map<String, Object> kwargs) {
            String needle = query == null ? "" : query.toLowerCase();
            List<Map<String, Object>> rows = collection(collection).values().stream()
                    .filter(row -> needle.isBlank()
                            || String.valueOf(row.getOrDefault("content", "")).toLowerCase().contains(needle)
                            || String.valueOf(row.getOrDefault("name", "")).toLowerCase().contains(needle))
                    .limit(Math.max(0, k))
                    .map(row -> {
                        Map<String, Object> copy = new LinkedHashMap<>(row);
                        copy.putIfAbsent("distance", 1.0d);
                        return copy;
                    })
                    .toList();
            return CompletableFuture.completedFuture(Map.of(collection, rows));
        }

        @Override
        public void attachEmbedder(Embedding embedder) {
            this.embedder = embedder;
        }

        @Override
        public void close() {
            collections.values().forEach(Map::clear);
        }

        private CompletableFuture<Void> addObjects(String collection, Iterable<?> objects) {
            Map<String, Map<String, Object>> target = collection(collection);
            for (Object object : objects) {
                Map<String, Object> row = graphObjectToMap(object);
                target.put(String.valueOf(row.get("uuid")), row);
            }
            return CompletableFuture.completedFuture(null);
        }

        private Map<String, Map<String, Object>> collection(String collection) {
            return collections.computeIfAbsent(collection, ignored -> new ConcurrentHashMap<>());
        }

        private static Map<String, Object> graphObjectToMap(Object object) {
            if (object instanceof Entity entity) {
                Map<String, Object> row = baseMap(entity);
                row.put("name", entity.getName());
                row.put("relations", entity.serializeRelations());
                row.put("episodes", entity.serializeEpisodes());
                row.put("attributes", entity.getAttributes());
                return row;
            }
            if (object instanceof Relation relation) {
                Map<String, Object> row = baseMap(relation);
                row.put("name", relation.getName());
                row.put("lhs", relation.serializeLhs());
                row.put("rhs", relation.serializeRhs());
                row.put("valid_since", relation.getValidSince());
                row.put("valid_until", relation.getValidUntil());
                row.put("offset_since", relation.getOffsetSince());
                row.put("offset_until", relation.getOffsetUntil());
                return row;
            }
            if (object instanceof Episode episode) {
                Map<String, Object> row = baseMap(episode);
                row.put("entities", episode.serializeEntities());
                row.put("valid_since", episode.getValidSince());
                return row;
            }
            if (object instanceof Map<?, ?> map) {
                Map<String, Object> row = stringObjectMap(map);
                row.putIfAbsent("uuid", GraphStoreUtils.getUuid());
                return row;
            }
            throw new IllegalArgumentException("Unsupported graph object: " + object);
        }

        private static Map<String, Object> baseMap(BaseGraphObject object) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uuid", object.getUuid());
            row.put("created_at", object.getCreatedAt());
            row.put("user_id", object.getUserId());
            row.put("obj_type", object.getObjType());
            row.put("language", object.getLanguage());
            row.put("content", object.getContent());
            row.put("metadata", object.getMetadata());
            return row;
        }
    }
}
