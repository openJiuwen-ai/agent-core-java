/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.store.base_embedding.Embedding;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphConstants;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphStoreFactory;
import com.openjiuwen.core.foundation.store.graph.GraphUtils;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.graph.AddMemStrategy;
import com.openjiuwen.core.memory.config.graph.EpisodeType;
import com.openjiuwen.core.memory.config.graph.GraphDefaults;
import com.openjiuwen.core.memory.config.graph.SearchConfig;
import com.openjiuwen.core.memory.graph.extraction.EntityDeclaration;
import com.openjiuwen.core.memory.graph.extraction.EntityDuplication;
import com.openjiuwen.core.memory.graph.extraction.EntityExtraction;
import com.openjiuwen.core.memory.graph.extraction.AIEntity;
import com.openjiuwen.core.memory.graph.extraction.EntityDef;
import com.openjiuwen.core.memory.graph.extraction.HumanEntity;
import com.openjiuwen.core.memory.graph.extraction.MergeRelations;
import com.openjiuwen.core.memory.graph.extraction.EntitySummary;
import com.openjiuwen.core.memory.graph.extraction.ParseResponse;
import com.openjiuwen.core.memory.graph.extraction.RelationExtraction;
import com.openjiuwen.core.memory.graph.extraction.RelevantFacts;
import com.openjiuwen.core.memory.graph.extraction.TimezonePredictions;
import com.openjiuwen.core.memory.graph.extraction.ExtractionPrompts;
import com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction.ExtractionPromptLanguageBase;
import com.openjiuwen.core.retrieval.reranker.Reranker;

import com.openjiuwen.spi.store.query.QueryExpr;
import com.openjiuwen.spi.store.query.QueryExpressions;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Graph memory that handles retrieval over knowledge graph memory.
 *
 * This is the current migrated subset of Python graph_memory/base.py: constructor,
 * backend wiring, search strategy registration, search, state init, and episode preparation.
 */
public class GraphMemory {
    private static final String STORE_TYPE = "graph mem store";

    /**
 * Public record SearchHit used by the Java parity implementation.
 *
 * @since 1.0
 */
public record SearchHit(double score, com.openjiuwen.core.foundation.store.graph.BaseGraphObject object) {
    }

    private final Map<String, Integer> tokenRecord = new LinkedHashMap<>();
    private final AddMemStrategy defaultExtractionStrategy;
    private Reranker reranker;
    private final String language;
    private final GraphStore dbBackend;
    private final GraphConfig config;
    private final Model llmClient;
    private final Map<String, Object> llmExtraKwargs;
    private final boolean isLlmStructuredOutputEnabled;
    private final ReentrantLock threadLock = new ReentrantLock();
    private final Map<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    private final boolean isDebugEnabled;
    private final long timeTillNextGc = 300L;
    private final boolean isMetricSimilarity;
    private final Map<String, List<SearchConfig>> searchStrategies = new LinkedHashMap<>();
    private long lastGcMillis = System.currentTimeMillis();
    private final Semaphore semaphore;

    /**
     * Auto-generated for codecheck compliance.
     */
    public GraphMemory(GraphConfig dbConfig,
                       Model llmClient,
                       boolean isLlmStructuredOutputEnabled,
                       Reranker reranker,
                       AddMemStrategy extractionStrategy,
                       Map<String, Object> dbKwargs,
                       Map<String, Object> llmExtraKwargs,
                       String language,
                       boolean isDebugEnabled) {
        this.tokenRecord.put("input_tokens", 0);
        this.tokenRecord.put("output_tokens", 0);
        this.defaultExtractionStrategy = extractionStrategy != null
                ? extractionStrategy
                : GraphDefaults.DEFAULT_STRATEGY;
        this.reranker = reranker;
        this.language = ExtractionPromptLanguageBase.ensureValidLanguage(language != null ? language : "cn",
                dbConfig.getDbStorageConfig().getLanguage());
        this.dbBackend = GraphStoreFactory.fromConfig(dbConfig);
        this.config = dbConfig;
        this.llmClient = llmClient;
        this.llmExtraKwargs = llmExtraKwargs != null ? new LinkedHashMap<>(llmExtraKwargs) : null;
        this.isLlmStructuredOutputEnabled = isLlmStructuredOutputEnabled;
        this.isDebugEnabled = isDebugEnabled;
        this.isMetricSimilarity = true;
        this.searchStrategies.put("default", List.of(
                new SearchConfig(),
                createDefaultRelationSearch(),
                createDefaultEpisodeSearch()
        ));
        this.semaphore = new Semaphore(Math.max(1, dbConfig.getWorkerThreads()));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public GraphMemory(GraphConfig dbConfig) {
        this(dbConfig, null, true, null, GraphDefaults.DEFAULT_STRATEGY, Map.of(), null, "cn", false);
    }

    private static SearchConfig createDefaultRelationSearch() {
        SearchConfig config = new SearchConfig();
        config.setMinScore(0.02);
        return config;
    }

    private static SearchConfig createDefaultEpisodeSearch() {
        SearchConfig config = new SearchConfig();
        config.setMinScore(0.025);
        return config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Embedding getEmbedder() {
        return dbBackend.getEmbedder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void attachEmbedder(Embedding embedder) {
        dbBackend.attachEmbedder(embedder);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void attachReranker(Reranker reranker) {
        if (reranker == null) {
            throw new IllegalArgumentException("Reranker must be an implementation of Reranker");
        }
        this.reranker = reranker;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void registerSearchStrategy(String name,
                                       SearchConfig searchEntity,
                                       SearchConfig searchRelation,
                                       SearchConfig searchEpisode,
                                       boolean isForceRegister) {
        List<SearchConfig> configs = List.of(searchEntity, searchRelation, searchEpisode);
        for (SearchConfig config : configs) {
            if (config != null && !(config instanceof SearchConfig)) {
                throw new IllegalArgumentException(
                        "Search config for entity/relation/episode must be an instance of SearchConfig or None");
            }
        }
        threadLock.lock();
        try {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Search config cannot be registered as an empty value.");
            }
            if (searchStrategies.containsKey(name) && !isForceRegister) {
                throw new IllegalArgumentException("Search config with name [" + name + "] already exists.");
            }
            searchStrategies.put(name, List.of(
                    searchEntity != null ? copySearchConfig(searchEntity) : new SearchConfig(),
                    searchRelation != null ? copySearchConfig(searchRelation) : createDefaultRelationSearch(),
                    searchEpisode != null ? copySearchConfig(searchEpisode) : createDefaultEpisodeSearch()
            ));
        } finally {
            threadLock.unlock();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void registerSearchStrategy(String name,
                                       SearchConfig searchEntity,
                                       SearchConfig searchRelation,
                                       SearchConfig searchEpisode) {
        registerSearchStrategy(name, searchEntity, searchRelation, searchEpisode, false);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void ensureThreadLock(String userId) {
        threadLock.lock();
        try {
            userLocks.computeIfAbsent(userId, ignored -> new ReentrantLock());
        } finally {
            threadLock.unlock();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, List<SearchHit>> search(String query,
                                               Object userId,
                                               String searchStrategy,
                                               boolean isEntityEnabled,
                                               boolean isRelationEnabled,
                                               boolean isEpisodeEnabled,
                                               List<Float> queryEmbedding) throws Exception {
        if (!searchStrategies.containsKey(searchStrategy)) {
            if (searchStrategy == null || searchStrategy.isBlank()) {
                throw new IllegalArgumentException("strategy must be a non-empty string value");
            }
            throw new IllegalArgumentException(
                    "Strategy [" + searchStrategy
                            + "] not found, please register with register_search_configs method or use \"default\".");
        }
        List<String> users = ValidateInput.validateSearchInput(
                query, userId, List.of(isEntityEnabled, isRelationEnabled, isEpisodeEnabled));
        List<Float> effectiveQueryEmbedding = queryEmbedding;
        if (queryEmbedding == null) {
            if (dbBackend.getEmbedder() == null) {
                throw new IllegalStateException("use the attach_embedder method to attach one");
            }
            effectiveQueryEmbedding = dbBackend.getEmbedder().embedQuery(query);
        }
        Map<String, List<SearchHit>> result = new LinkedHashMap<>();
        if (isEntityEnabled) {
            performSearch(0, users, searchStrategy, result, query, effectiveQueryEmbedding);
        }
        if (isRelationEnabled) {
            performSearch(1, users, searchStrategy, result, query, effectiveQueryEmbedding);
        }
        if (isEpisodeEnabled) {
            performSearch(2, users, searchStrategy, result, query, effectiveQueryEmbedding);
        }
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public States.GraphMemUpdate addMemory(EpisodeType srcType,
                                           String userId,
                                           Object content,
                                           Map<String, String> contentFmtKwargs,
                                           OffsetDateTime referenceTime) throws Exception {
        ensureThreadLock(userId);
        if (dbBackend.getEmbedder() == null) {
            throw new IllegalStateException("use the attach_embedder method to attach one");
        }
        if (llmClient == null) {
            throw new IllegalStateException("llm_client is required for addMemory");
        }
        ReentrantLock userLock = userLocks.get(userId);
        userLock.lock();
        try {
            States.GraphMemState state = initState(referenceTime);
            String preparedContent = prepareEpisodes(srcType, userId, content, state, contentFmtKwargs);
            Episode currentEpisode = PostprocessGraphObjects.createEpisode(dbBackend, userId, preparedContent, state);
            String contentWithTime = GraphUtils.formatTimestamp(state.getReferenceTimestamp(), java.time.ZoneOffset.UTC,
                    "(EEE) yyyy/MMM/dd HH:mm:ss") + "\n" + preparedContent;

            AssistantMessage timezoneResponse = invokeLlm(
                    ExtractionPrompts.extractTimezone(contentWithTime, state.getHistory(), null,
                            state.getPrompting().getLanguage(), 2),
                    Map.of()
            );

            EntityDeclarationResult declarationResult = extractEntityDeclarations(srcType, contentWithTime, state);
            Object timezoneInfo = ParseResponse.parseJson(
                    timezoneResponse.getContentAsString(),
                    com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel.responseFormat(
                            TimezonePredictions.class, state.getPrompting().getLanguage())
            );

            AssistantMessage relationResponse = invokeLlm(
                    ExtractionPrompts.extractRelationDeclaration(
                            null,
                            declarationResult.entities(),
                            state.getReferenceTimestamp(),
                            timezoneInfo != null ? timezoneInfo : List.of(),
                            contentWithTime,
                            state.getHistory(),
                            state.getEntityTypes(),
                            null,
                            state.getPrompting().getRelationExtractionLanguage(),
                            2
                    ),
                    Map.of()
            );

            fetchRelevantEntities(
                    declarationResult.entities(),
                    declarationResult.existingEntityMissing(),
                    userId,
                    state);
            List<Object> mergedDeclarations = entityMerge(declarationResult.entities(), state);

            Object relationParsed = ParseResponse.parseJson(
                    relationResponse.getContentAsString(),
                    com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel.responseFormat(
                            RelationExtraction.class, state.getPrompting().getRelationExtractionLanguage())
            );
            List<Map<String, Object>> relationList = normalizeRelationList(relationParsed);
            Map.Entry<List<Relation>, List<Entity>> parsed = ParseLlmResponse.parseAllRelations(
                    relationList,
                    new ArrayList<>(mergedDeclarations),
                    state.getEntityTypes(),
                    Map.of(
                            "created_at", state.getReferenceTimestamp(),
                            "user_id", userId,
                            "language", state.getPrompting().getLanguage()
                    )
            );

            List<Entity> entities = entityEnrich(parsed.getValue(), contentWithTime, state);
            parseRelationFilteringResult(parsed.getKey(), state);
            handleRelationDedupe(userId, contentWithTime, parsed.getKey(), state);
            updateEntitiesForRelationRemoval(state, mergedDeclarations);

            PostprocessGraphObjects.processRelations(dbBackend, entities, parsed.getKey(), state);
            PostprocessGraphObjects.processEntities(dbBackend, entities, currentEpisode, state);
            PostprocessGraphObjects.validateEntitiesEpisodes(entities, currentEpisode, state);
            States.persistToDb(dbBackend, state, config);
            dbBackend.refresh();
            return state.getMemUpdate().or(state.getMemUpdateSkipEmbed());
        } finally {
            userLock.unlock();
        }
    }

    private void performSearch(int collectionIndex,
                               List<String> userId,
                               String searchStrategy,
                               Map<String, List<SearchHit>> result,
                               String query,
                               List<Float> queryEmbedding) throws Exception {
        List<String> names = List.of(
                GraphConstants.ENTITY_COLLECTION,
                GraphConstants.RELATION_COLLECTION,
                GraphConstants.EPISODE_COLLECTION);
        SearchConfig configEntry = copySearchConfig(searchStrategies.get(searchStrategy).get(collectionIndex));
        if (configEntry.isRerank() && reranker == null) {
            throw new IllegalArgumentException(
                    "Search strategy [" + searchStrategy + "] for " + names.get(collectionIndex)
                            + " has rerank=True but reranker is not set, please use the attach_reranker "
                            + "method to attach a reranker.");
        }
        QueryExpr filterByUser = QueryExpressions.filterUser(userId);
        configEntry.setFilterExpr(configEntry.getFilterExpr() != null
                ? configEntry.getFilterExpr().and(filterByUser)
                : filterByUser);
        List<Map<String, Object>> returned = searchSingle(
                names.get(collectionIndex),
                query,
                configEntry,
                queryEmbedding);
        List<SearchHit> hits = new ArrayList<>();
        for (Map<String, Object> item : returned) {
            double score = Double.parseDouble(String.valueOf(item.getOrDefault("distance", 0.0)));
            hits.add(new SearchHit(score, toGraphObject(names.get(collectionIndex), item)));
        }
        result.put(names.get(collectionIndex), hits);
    }

    private List<Map<String, Object>> searchSingle(String collection,
                                                   String query,
                                                   SearchConfig searchConfig,
                                                   List<Float> queryEmbedding) throws Exception {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("language", searchConfig.getLanguage());
        kwargs.put("min_score", searchConfig.getMinScore());
        if (searchConfig.isRerank()) {
            kwargs.put("reranker", reranker);
        }
        Map<String, List<Map<String, Object>>> result = dbBackend.search(
                query,
                searchConfig.getTopK(),
                collection,
                searchConfig.getRankConfig(),
                searchConfig.getBfsDepth(),
                searchConfig.getBfsK(),
                searchConfig.getFilterExpr(),
                searchConfig.getOutputFields(),
                queryEmbedding,
                kwargs
        );
        return result.getOrDefault(collection, List.of());
    }

    private com.openjiuwen.core.foundation.store.graph.BaseGraphObject toGraphObject(
            String collection,
            Map<String, Object> map) {
        return switch (collection) {
            case GraphConstants.ENTITY_COLLECTION -> mapToEntity(map);
            case GraphConstants.RELATION_COLLECTION -> mapToRelation(map);
            case GraphConstants.EPISODE_COLLECTION -> mapToEpisode(map);
            default -> throw new IllegalArgumentException("Unknown collection: " + collection);
        };
    }

    private Entity mapToEntity(Map<String, Object> map) {
        Entity entity = new Entity();
        populateBaseGraphObject(entity, map, "Entity");
        entity.setName(String.valueOf(map.getOrDefault("name", "")));
        entity.setNameEmbedding(floatList(map.get("name_embedding")));
        entity.setRelations(objectList(map.get("relations")));
        entity.setEpisodes(stringList(map.get("episodes")));
        entity.setAttributes(objectMap(map.get("attributes")));
        return entity;
    }

    private Relation mapToRelation(Map<String, Object> map) {
        Relation relation = new Relation();
        populateBaseGraphObject(relation, map, "Relation");
        relation.setName(String.valueOf(map.getOrDefault("name", "")));
        relation.setValidSince(intValue(map.get("valid_since"), -1));
        relation.setValidUntil(intValue(map.get("valid_until"), -1));
        relation.setOffsetSince(intValue(map.get("offset_since"), 0));
        relation.setOffsetUntil(intValue(map.get("offset_until"), 0));
        relation.setLhs(map.get("lhs"));
        relation.setRhs(map.get("rhs"));
        return relation;
    }

    private Episode mapToEpisode(Map<String, Object> map) {
        Episode episode = new Episode();
        populateBaseGraphObject(episode, map, "Episode");
        episode.setValidSince(intValue(map.get("valid_since"), -1));
        episode.setEntities(objectList(map.get("entities")));
        return episode;
    }

    private void populateBaseGraphObject(
            com.openjiuwen.core.foundation.store.graph.BaseGraphObject target,
            Map<String, Object> map,
            String defaultType) {
        target.setUuid(String.valueOf(map.getOrDefault("uuid", GraphUtils.getUuid())));
        target.setCreatedAt(intValue(map.get("created_at"), GraphUtils.getCurrentUtcTimestamp()));
        target.setUserId(String.valueOf(map.getOrDefault("user_id", "default_user")));
        target.setObjType(String.valueOf(map.getOrDefault("obj_type", defaultType)));
        target.setLanguage(String.valueOf(map.getOrDefault("language", "cn")));
        target.setMetadata(objectMap(map.get("metadata")));
        target.setContent(String.valueOf(map.getOrDefault("content", "")));
        target.setContentEmbedding(floatList(map.get("content_embedding")));
        target.setContentBm25(floatList(map.get("content_bm25")));
    }

    private int intValue(Object value, int defaultValue) {
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private List<Object> objectList(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return list.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<Float> floatList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Float> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                result.add(number.floatValue());
            }
        }
        return result;
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private SearchConfig copySearchConfig(SearchConfig source) {
        SearchConfig copy = new SearchConfig();
        copy.setTopK(source.getTopK());
        copy.setMinScore(source.getMinScore());
        copy.setRankConfig(source.getRankConfig());
        copy.setBfsK(source.getBfsK());
        copy.setBfsDepth(source.getBfsDepth());
        copy.setFilterExpr(source.getFilterExpr());
        copy.setOutputFields(source.getOutputFields());
        copy.setRerank(source.isRerank());
        copy.setLanguage(source.getLanguage());
        return copy;
    }

    private States.GraphMemState initState(OffsetDateTime referenceTime) {
        AddMemStrategy strategy = defaultExtractionStrategy;
        States.GraphMemState state = new States.GraphMemState();
        state.setStrategy(strategy);
        state.getEntityTypes().add(new EntityDef());
        state.getEntityTypes().add(new HumanEntity());
        state.getEntityTypes().add(new AIEntity());
        state.getPrompting().setLanguage(language);
        state.getPrompting().setEntityExtractionLanguage(strategy.isChineseEntity() ? "cn" : language);
        state.getPrompting().setRelationExtractionLanguage(strategy.isChineseRelation() ? "cn" : language);
        state.getPrompting().setEntityDedupeLanguage(strategy.isChineseEntityDedupe() ? "cn" : language);
        state.getPrompting().setSchemaEntityExtraction(
                com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel.responseFormat(
                        EntitySummary.class,
                        language));
        state.getPrompting().setSchemaEntityDedupe(
                com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel.responseFormat(
                        EntityDuplication.class,
                        state.getPrompting().getEntityDedupeLanguage()));
        state.getPrompting().setSchemaRelationMerge(
                com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel.responseFormat(
                        MergeRelations.class,
                        language));
        state.getPrompting().setSchemaRelationFilter(
                com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel.responseFormat(
                        RelevantFacts.class,
                        language));
        state.getExtras().put("summary_target", String.valueOf(strategy.getSummaryTarget()));
        if (referenceTime == null) {
            state.setReferenceTimestamp(state.getCurrentTimestamp());
        } else {
            state.setReferenceTimestamp((int) referenceTime.toEpochSecond());
        }
        return state;
    }

    private String prepareEpisodes(EpisodeType srcType,
                                   String userId,
                                   Object content,
                                   States.GraphMemState state,
                                   Map<String, String> contentFmtKwargs) throws Exception {
        ValidateInput.validateAddMemoryInput(
                config.getDbStorageConfig().getUserId(),
                srcType,
                userId,
                contentFmtKwargs);
        String normalizedContent;
        if (content instanceof String stringContent) {
            if (contentFmtKwargs != null && !contentFmtKwargs.isEmpty()) {
                throw new IllegalArgumentException(
                        "content_fmt_kwargs has no effect when content is str, please leave it empty");
            }
            normalizedContent = stringContent;
        } else if (srcType == EpisodeType.CONVERSATION && content instanceof List<?> listContent) {
            List<Map<String, Object>> messages = GraphMemoryUtils.msg2dict(listContent, false);
            Map<String, String> formatKwargs = contentFmtKwargs != null
                    ? contentFmtKwargs
                    : Map.of();
            normalizedContent = GraphUtils.formatListOfMessages(
                    messages,
                    new LinkedHashMap<>(formatKwargs),
                    "{role}: {content}\n");
        } else {
            throw new IllegalArgumentException(
                    "The content must be str when source type is not conversation");
        }
        normalizedContent = normalizedContent.trim();
        if (normalizedContent.isBlank()) {
            throw new IllegalArgumentException(
                    "content must be a non-empty value of either a str or a list of messages in OpenAI "
                            + "(dict[str, str]) / openJiuwen (BaseMessage) standard");
        }

        List<Episode> historyEpisodes = new ArrayList<>();
        var recallStrategy = state.getStrategy().getRecallEpisode();
        boolean isMaximizeScore = isMetricSimilarity || recallStrategy.getRankConfig().isHigherIsBetter();
        if (recallStrategy.getTopK() > 0 && !dbBackend.isEmpty(GraphConstants.EPISODE_COLLECTION)) {
            List<QueryExpr> filters = new ArrayList<>();
            if (userId != null && !userId.isBlank()) {
                filters.add(QueryExpressions.filterUser(userId));
            }
            if (recallStrategy.isSameKind()) {
                filters.add(QueryExpressions.eq("obj_type", srcType.name()));
            }
            if (recallStrategy.isExcludeFutureResults()) {
                filters.add(QueryExpressions.lte("valid_since", state.getReferenceTimestamp()));
            }
            QueryExpr episodeSearchQuery = QueryExpressions.chainFilters(filters);
            List<Map<String, Object>> raw = dbBackend.search(
                    normalizedContent,
                    recallStrategy.getTopK(),
                    GraphConstants.EPISODE_COLLECTION,
                    recallStrategy.getRankConfig(),
                    0,
                    0,
                    episodeSearchQuery,
                    null,
                    null,
                    Map.of("language", state.getPrompting().getLanguage())
            ).getOrDefault(GraphConstants.EPISODE_COLLECTION, List.of());
            for (Map<String, Object> item : raw) {
                double distance = Double.parseDouble(String.valueOf(item.getOrDefault("distance", 1.0)));
                if ((isMaximizeScore && distance >= recallStrategy.getMinScore())
                        || (!isMaximizeScore && distance <= recallStrategy.getMinScore())) {
                    historyEpisodes.add(mapToEpisode(item));
                }
            }
            historyEpisodes.sort(java.util.Comparator.comparingInt(Episode::getValidSince));
        }
        for (Episode episode : historyEpisodes) {
            state.getLookupTable().getEpisodes().put(episode.getUuid(), episode);
        }
        List<String> history = new ArrayList<>();
        for (Episode episode : historyEpisodes) {
            history.add(GraphUtils.formatTimestamp(
                    episode.getCreatedAt(),
                    java.time.ZoneOffset.UTC,
                    "(EEE) yyyy/MMM/dd HH:mm:ss")
                    + "\n" + episode.getContent());
        }
        state.setHistory(String.join("\n---\n", history));
        return normalizedContent;
    }

    private AssistantMessage invokeLlm(ExtractionPrompts.PromptCall promptCall,
                                       Map<String, Object> extra) throws Exception {
        if (promptCall.template() == null) {
            throw new IllegalStateException("prompt template not found");
        }
        Map<String, Object> params = GraphMemoryUtils.assembleInvokeParams(promptCall.kwargs(), promptCall.template(),
                isLlmStructuredOutputEnabled ? promptCall.outputModel() : null);
        if (llmExtraKwargs != null) {
            params.putAll(llmExtraKwargs);
        }
        if (extra != null) {
            params.putAll(extra);
        }
        semaphore.acquire();
        try {
            return llmClient.invoke(
                    params.get("messages"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    params
            );
        } finally {
            semaphore.release();
        }
    }

    private EntityDeclarationResult extractEntityDeclarations(EpisodeType srcType,
                                                              String content,
                                                              States.GraphMemState state) throws Exception {
        AssistantMessage response = invokeLlm(
                ExtractionPrompts.extractEntityDeclaration(
                        srcType,
                        content,
                        state.getHistory(),
                        null,
                        state.getEntityTypes(),
                        state.getPrompting().getEntityExtractionLanguage(),
                        state.getExtras(),
                        2
                ),
                Map.of()
        );
        Object parsed = ParseResponse.parseJson(
                response.getContentAsString(),
                com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel.responseFormat(
                        EntityExtraction.class, state.getPrompting().getEntityExtractionLanguage())
        );
        List<Map<String, Object>> declarationsRaw = normalizeDeclarationList(parsed);
        List<EntityDeclaration> declarations = new ArrayList<>();
        List<String> names = new ArrayList<>(List.of("user", "assistant", "User", "Assistant", "USER", "ASSISTANT"));
        for (Map<String, Object> raw : declarationsRaw) {
            Object nameObj = firstPresent(raw, List.of("name"));
            String name = nameObj != null ? String.valueOf(nameObj).trim() : "";
            if (name.isBlank() || names.contains(name)) {
                continue;
            }
            names.add(name);
            Object typeObj = firstPresent(raw, List.of("entity_type_id", "entityTypeId"));
            EntityDeclaration declaration = new EntityDeclaration();
            declaration.setName(name);
            declaration.setEntityTypeId(typeObj != null ? Integer.parseInt(String.valueOf(typeObj)) : 0);
            declarations.add(declaration);
        }
        boolean existingEntityMissing = dbBackend.isEmpty(GraphConstants.ENTITY_COLLECTION);
        return new EntityDeclarationResult(existingEntityMissing, declarations);
    }

    private void fetchRelevantEntities(List<EntityDeclaration> extractedDeclarations,
                                       boolean existingEntityMissing,
                                       String userId,
                                       States.GraphMemState state) throws Exception {
        if (existingEntityMissing || extractedDeclarations.isEmpty()) {
            return;
        }
        List<List<Float>> entityEmbeddings = dbBackend.getEmbedder() != null
                ? dbBackend.getEmbedder().embedDocuments(
                        extractedDeclarations.stream().map(EntityDeclaration::getName).toList(),
                        config.getEmbedBatchSize())
                : List.of();
        int idx = 0;
        for (EntityDeclaration entity : extractedDeclarations) {
            List<Float> embedding = idx < entityEmbeddings.size() ? entityEmbeddings.get(idx) : null;
            idx++;
            EntityDef entityType = entity.getEntityTypeId() < state.getEntityTypes().size()
                    ? state.getEntityTypes().get(entity.getEntityTypeId()) : null;
            if (embedding != null) {
                List<Map<String, Object>> result = dbBackend.search(
                        entity.getName(),
                        state.getStrategy().getRecallEntity().getTopK(),
                        GraphConstants.ENTITY_COLLECTION,
                        state.getStrategy().getRecallEntity().getRankConfig(),
                        0,
                        0,
                        QueryExpressions.filterUser(userId),
                        null,
                        embedding,
                        Map.of("language", state.getPrompting().getLanguage())
                ).getOrDefault(GraphConstants.ENTITY_COLLECTION, List.of());
                for (Map<String, Object> found : result) {
                    state.getRetrievedEntities().put(String.valueOf(found.get("uuid")), mapToEntity(found));
                }
                if (entityType != null) {
                    result = dbBackend.search(
                            entity.getName(),
                            state.getStrategy().getRecallEntity().getTopK(),
                            GraphConstants.ENTITY_COLLECTION,
                            state.getStrategy().getRecallEntity().getRankConfig(),
                            0,
                            0,
                            QueryExpressions.filterUser(userId)
                                    .and(QueryExpressions.eq("obj_type", entityType.getName())),
                            null,
                            embedding,
                            Map.of("language", state.getPrompting().getLanguage())
                    ).getOrDefault(GraphConstants.ENTITY_COLLECTION, List.of());
                    for (Map<String, Object> found : result) {
                        state.getRetrievedEntities().put(String.valueOf(found.get("uuid")), mapToEntity(found));
                    }
                }
            }
            List<Map<String, Object>> result = dbBackend.query(
                    GraphConstants.ENTITY_COLLECTION,
                    null,
                    QueryExpressions.filterUser(userId),
                    true
            );
            for (Map<String, Object> found : result) {
                String existingName = String.valueOf(found.getOrDefault("name", ""));
                if (existingName.equals(entity.getName())
                        || existingName.contains(entity.getName())
                        || entity.getName().contains(existingName)) {
                    state.getRetrievedEntities().put(String.valueOf(found.get("uuid")), mapToEntity(found));
                }
            }
        }
    }

    private List<Object> entityMerge(List<EntityDeclaration> declarations,
                                     States.GraphMemState state) throws Exception {
        if (state.getRetrievedEntities().isEmpty()) {
            return new ArrayList<>(declarations);
        }
        List<Map<String, Object>> existingEntities = state.getRetrievedEntities().values().stream()
                .map(Entity::toMap)
                .toList();
        AssistantMessage response = invokeLlm(
                ExtractionPrompts.dedupeEntityList(
                        "",
                        declarations,
                        existingEntities,
                        state.getEntityTypes(),
                        state.getHistory(),
                        null,
                        state.getPrompting().getEntityDedupeLanguage(),
                        2
                ),
                Map.of()
        );
        Object parsed = ParseResponse.parseJson(
                response.getContentAsString(),
                com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel.responseFormat(
                        EntityDuplication.class, state.getPrompting().getEntityDedupeLanguage())
        );
        List<Map<String, Object>> duplication = normalizeDuplicationList(parsed);
        ParseLlmResponse.ResolveEntitiesResult isResolved = ParseLlmResponse.resolveEntities(
                declarations,
                new ArrayList<>(state.getRetrievedEntities().values()),
                duplication
        );
        if (state.getStrategy().isMergeEntities()) {
            state.getMemUpdate().getRemovedEntity().addAll(isResolved.entityUuidsToRemove());
        }
        if (state.getStrategy().isMergeEntities() && !isResolved.mergingArgs().isEmpty()) {
            resolveEntityMerges(isResolved.mergingArgs(), state);
        }
        return new ArrayList<>(isResolved.resolvedEntities());
    }

    private List<Entity> entityEnrich(List<Entity> entities,
                                      String content,
                                      States.GraphMemState state) throws Exception {
        for (Entity entity : entities) {
            AssistantMessage response = invokeLlm(
                    ExtractionPrompts.extractEntityAttributes(
                            entity,
                            content,
                            state.getHistory(),
                            state.getPrompting().getLanguage(),
                            state.getExtras(),
                            2
                    ),
                    Map.of()
            );
            GraphMemoryUtils.updateEntity(
                    entity,
                    response.getContentAsString(),
                    state.getPrompting().getSchemaEntityExtraction());
        }
        return entities;
    }

    private void parseRelationFilteringResult(List<Relation> relations,
                                              States.GraphMemState state) {
        if (!state.getRelationFilterTasks().isEmpty()) {
            for (Map.Entry<CompletableFuture<?>, Object> entry : state.getRelationFilterTasks().entrySet()) {
                CompletableFuture<?> task = entry.getKey();
                @SuppressWarnings("unchecked")
                Map.Entry<Entity, List<Relation>> payload = (Map.Entry<Entity, List<Relation>>) entry.getValue();
                Entity targetEntity = payload.getKey();
                List<Relation> newRelationList = payload.getValue();
                String targetUuid = targetEntity.getUuid();
                List<Relation> filteredRelations;
                try {
                    Object response = task.join();
                    String content = response instanceof AssistantMessage message
                            ? message.getContentAsString()
                            : String.valueOf(response);
                    Object parsed = ParseResponse.parseJson(content, state.getPrompting().getSchemaRelationFilter());
                    Set<Integer> keepIds = new HashSet<>();
                    if (parsed instanceof Map<?, ?> map) {
                        Map<String, Object> parsedMap = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> mapEntry : map.entrySet()) {
                            parsedMap.put(String.valueOf(mapEntry.getKey()), mapEntry.getValue());
                        }
                        Object relevant = firstPresent(
                                parsedMap,
                                List.of("relevant_relations", "relevantRelations"));
                        if (relevant instanceof List<?> list) {
                            for (Object value : list) {
                                keepIds.add(Integer.parseInt(String.valueOf(value)));
                            }
                        }
                    }
                    filteredRelations = new ArrayList<>();
                    for (Integer keepId : keepIds) {
                        if (keepId > 0 && keepId <= newRelationList.size()) {
                            filteredRelations.add(newRelationList.get(keepId - 1));
                        }
                    }
                } catch (RuntimeException e) {
                    filteredRelations = newRelationList;
                }
                state.getMergeInfos().get(targetUuid).getNewRelations().clear();
                state.getMergeInfos().get(targetUuid).getNewRelations().addAll(filteredRelations);
            }
            for (Map.Entry<String, States.EntityMerge> entry : state.getMergeInfos().entrySet()) {
                String targetUuid = entry.getKey();
                States.EntityMerge mergeInfo = entry.getValue();
                for (Object deferred : state.getRelationDeferredUpdates().getOrDefault(targetUuid, List.of())) {
                    if (!(deferred instanceof List<?> tuple) || tuple.size() < 3) {
                        continue;
                    }
                    Object relationObj = tuple.get(0);
                    if (!(relationObj instanceof Relation relation)) {
                        continue;
                    }
                    String attr = String.valueOf(tuple.get(1));
                    String value = String.valueOf(tuple.get(2));
                    if (mergeInfo.getNewRelations().contains(relation)) {
                        if ("lhs".equals(attr)) {
                            relation.setLhs(value);
                        } else {
                            relation.setRhs(value);
                        }
                        if (!state.getMemUpdateSkipEmbed().getUpdatedRelation().contains(relation)) {
                            state.getMemUpdateSkipEmbed().getUpdatedRelation().add(relation);
                        }
                    } else {
                        state.getMemUpdate().getRemovedRelation().add(relation.getUuid());
                        state.getToRemove().add(relation);
                    }
                }
            }
        }
        States.classifyRelationsExtracted(relations, state);
    }

    private void handleRelationDedupe(String userId,
                                      String content,
                                      List<Relation> relations,
                                      States.GraphMemState state) throws Exception {
        relations.removeIf(state.getToRemove()::contains);
        if (relations.isEmpty() || dbBackend.isEmpty(GraphConstants.RELATION_COLLECTION)
                || !state.getStrategy().isMergeRelations() || dbBackend.getEmbedder() == null) {
            return;
        }
        List<List<Float>> embeddings = dbBackend.getEmbedder().embedDocuments(
                relations.stream().map(Relation::getContent).toList(),
                config.getEmbedBatchSize()
        );
        relationDedupe(userId, content, relations, embeddings, state);
    }

    private void updateEntitiesForRelationRemoval(States.GraphMemState state,
                                                  List<Object> extractedDeclarations) throws Exception {
        if (state.getMemUpdate().getRemovedRelation().isEmpty()) {
            return;
        }
        for (Entity entity : state.getMemUpdate().getUpdatedEntity()) {
            entity.getRelations().removeIf(relation -> {
                String relationUuid = relation instanceof String s ? s : ((Relation) relation).getUuid();
                return state.getMemUpdate().getRemovedRelation().contains(relationUuid);
            });
        }
        Set<String> entitiesToUpdate = new HashSet<>();
        for (Object relationObj : state.getToRemove()) {
            if (relationObj instanceof Relation relation) {
                entitiesToUpdate.add(relation.getLhs() instanceof String s
                        ? s
                        : ((Entity) relation.getLhs()).getUuid());
                entitiesToUpdate.add(relation.getRhs() instanceof String s
                        ? s
                        : ((Entity) relation.getRhs()).getUuid());
            } else if (relationObj instanceof String relationUuid) {
                Relation relation = state.getLookupTable().getRelations().get(relationUuid);
                if (relation != null) {
                    entitiesToUpdate.add(relation.getLhs() instanceof String s
                            ? s
                            : ((Entity) relation.getLhs()).getUuid());
                    entitiesToUpdate.add(relation.getRhs() instanceof String s
                            ? s
                            : ((Entity) relation.getRhs()).getUuid());
                }
            }
        }
        if (!entitiesToUpdate.isEmpty()) {
            List<Map<String, Object>> queryResult = dbBackend.query(GraphConstants.ENTITY_COLLECTION,
                    new ArrayList<>(entitiesToUpdate.stream().map(Object.class::cast).toList()), null, true);
            for (Map<String, Object> entityMap : queryResult) {
                Entity entity = state.getLookupTable().getEntities().getOrDefault(
                        String.valueOf(entityMap.get("uuid")), mapToEntity(entityMap));
                boolean updateWithoutEmbed = false;
                boolean needsReEmbed = false;
                for (Object extracted : extractedDeclarations) {
                    if (extracted instanceof EntityDeclaration declaration
                            && entity.getName().equals(declaration.getName())) {
                        needsReEmbed = true;
                        break;
                    }
                }
                List<Object> updatedRelations = new ArrayList<>();
                for (Object relationRef : entity.getRelations()) {
                    String relationUuid = relationRef instanceof String s ? s : ((Relation) relationRef).getUuid();
                    if (!state.getMemUpdate().getRemovedRelation().contains(relationUuid)) {
                        updatedRelations.add(relationRef);
                    } else if (!needsReEmbed) {
                        updateWithoutEmbed = true;
                    }
                }
                entity.setRelations(updatedRelations);
                if (updateWithoutEmbed
                        && !state.getMemUpdateSkipEmbed().getUpdatedEntity().contains(entity)
                        && !state.getMemUpdate().getRemovedEntity().contains(entity.getUuid())) {
                    state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entity);
                }
            }
        }
    }

    private void resolveEntityMerges(List<Map.Entry<Entity, List<Entity>>> mergingArgs,
                                     States.GraphMemState state) throws Exception {
        Set<String> episodesToUpdate = new HashSet<>();
        Map<String, Map<String, Relation>> entityRelationUpdates = new LinkedHashMap<>();
        Map<String, String> mapSrcToTarget = new LinkedHashMap<>();
        for (Map.Entry<Entity, List<Entity>> entry : mergingArgs) {
            Entity targetEntity = entry.getKey();
            String targetUuid = targetEntity.getUuid();
            States.EntityMerge mergeInfo = new States.EntityMerge(targetEntity);
            for (Entity source : entry.getValue()) {
                mergeInfo.getSource().put(source.getUuid(), source);
            }
            state.getMergeInfos().put(targetUuid, mergeInfo);
            Set<String> alias = new HashSet<>(mergeInfo.getSource().keySet());
            alias.add(targetUuid);
            state.getRelationDeferredUpdates().put(targetUuid, new ArrayList<>());
            entityRelationUpdates.put(targetUuid, new LinkedHashMap<>());
            for (Entity sourceEntity : entry.getValue()) {
                mapSrcToTarget.put(sourceEntity.getUuid(), targetUuid);
                targetEntity.getEpisodes().addAll(sourceEntity.getEpisodes());
                targetEntity.setEpisodes(targetEntity.getEpisodes().stream().distinct().toList());
                episodesToUpdate.addAll(sourceEntity.getEpisodes());
                if (!sourceEntity.getRelations().isEmpty()) {
                    resolveEachRelation(targetUuid, sourceEntity, mapSrcToTarget, entityRelationUpdates, state, alias);
                }
            }
        }
        state.getMemUpdate().getRemovedRelation().addAll(state.getFaultyRelations().keySet());
        dispatchEntityMergeTasks(episodesToUpdate, entityRelationUpdates, state);
    }

    private void dispatchEntityMergeTasks(Set<String> episodesToUpdate,
                                          Map<String, Map<String, Relation>> entityRelationUpdates,
                                          States.GraphMemState state) throws Exception {
        if (state.getStrategy().isMergeFilter()) {
            for (Map.Entry<String, Map<String, Relation>> entry : entityRelationUpdates.entrySet()) {
                String targetUuid = entry.getKey();
                List<Relation> relationList = entry.getValue().values().stream()
                        .filter(relation -> !state.getFaultyRelations().containsKey(relation.getUuid()))
                        .toList();
                if (relationList.isEmpty()) {
                    continue;
                }
                Entity targetEntity = state.getLookupTable().getEntities().get(targetUuid);
                if (targetEntity == null && state.getMergeInfos().containsKey(targetUuid)) {
                    targetEntity = state.getMergeInfos().get(targetUuid).getTarget();
                }
                state.getMergeInfos().get(targetUuid).getNewRelations().clear();
                state.getMergeInfos().get(targetUuid).getNewRelations().addAll(relationList);
                Entity finalTargetEntity = targetEntity;
                CompletableFuture<AssistantMessage> task = CompletableFuture.supplyAsync(() -> {
                    try {
                        return invokeLlm(
                                ExtractionPrompts.filterRelationsForMerge(
                                        finalTargetEntity,
                                        relationList,
                                        state.getPrompting().getLanguage(),
                                        state.getExtras(),
                                        2
                                ),
                                Map.of()
                        );
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                });
                state.getRelationFilterTasks().put(task, Map.entry(targetEntity, relationList));
            }
        }
        if (!episodesToUpdate.isEmpty()) {
            List<Map<String, Object>> queryResult = dbBackend.query(GraphConstants.EPISODE_COLLECTION,
                    new ArrayList<>(episodesToUpdate.stream().map(Object.class::cast).toList()), null, false);
            for (Map<String, Object> item : queryResult) {
                state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(mapToEpisode(item));
            }
        }
    }

    private void resolveEachRelation(String targetUuid,
                                     Entity sourceEntity,
                                     Map<String, String> mapSrcToTarget,
                                     Map<String, Map<String, Relation>> entityRelationUpdates,
                                     States.GraphMemState state,
                                     Set<String> alias) throws Exception {
        Set<String> selfPointing = new HashSet<>();
        List<Object> relationIds = sourceEntity.getRelations().stream()
                .map(rel -> rel instanceof String s ? s : ((Relation) rel).getUuid())
                .map(Object.class::cast).toList();
        List<Map<String, Object>> queryResult = dbBackend.query(
                GraphConstants.RELATION_COLLECTION,
                relationIds,
                null,
                false);
        List<Relation> sourceRelations = new ArrayList<>();
        for (Map<String, Object> relationMap : queryResult) {
            Relation relation = mapToRelation(relationMap);
            state.getLookupTable().getRelations().put(relation.getUuid(), relation);
            sourceRelations.add(relation);
        }
        for (Relation relation : sourceRelations) {
            String lhs = relation.getLhs() instanceof String s ? s : ((Entity) relation.getLhs()).getUuid();
            String rhs = relation.getRhs() instanceof String s ? s : ((Entity) relation.getRhs()).getUuid();
            if (alias.contains(lhs) && alias.contains(rhs)) {
                state.getFaultyRelations().put(relation.getUuid(), relation);
                selfPointing.add(relation.getUuid());
                continue;
            }
            String toReplace = sourceEntity.getUuid();
            while (mapSrcToTarget.containsKey(toReplace)
                    && !state.getFaultyRelations().containsKey(relation.getUuid())) {
                if (lhs.equals(toReplace)) {
                    replaceOneSideOfRelation("lhs", relation, targetUuid, entityRelationUpdates, state);
                    break;
                }
                if (rhs.equals(toReplace)) {
                    replaceOneSideOfRelation("rhs", relation, targetUuid, entityRelationUpdates, state);
                    break;
                }
                toReplace = mapSrcToTarget.get(toReplace);
            }
            if (!selfPointing.contains(relation.getUuid())
                    && !entityRelationUpdates.get(targetUuid).containsKey(relation.getUuid())) {
                state.getFaultyRelations().put(relation.getUuid(), relation);
            }
        }
    }

    private void replaceOneSideOfRelation(String side,
                                          Relation relation,
                                          String targetUuid,
                                          Map<String, Map<String, Relation>> entityRelationUpdates,
                                          States.GraphMemState state) {
        Map<String, Relation> relationMap = entityRelationUpdates.get(targetUuid);
        if (!relationMap.containsKey(relation.getUuid())) {
            state.getRelationDeferredUpdates().get(targetUuid).add(List.of(relation, side, targetUuid));
            relationMap.put(relation.getUuid(), relation);
        } else {
            state.getFaultyRelations().put(relation.getUuid(), relation);
            relationMap.remove(relation.getUuid());
            state.getRelationDeferredUpdates().get(targetUuid).removeIf(tuple -> {
                if (!(tuple instanceof List<?> list) || list.isEmpty()) {
                    return false;
                }
                return list.get(0) == relation;
            });
        }
    }

    private void relationDedupe(String userId,
                                String content,
                                List<Relation> relations,
                                List<List<Float>> relationEmbedResults,
                                States.GraphMemState state) throws Exception {
        List<PostprocessGraphObjects.RelationTask> dedupeTasks = new ArrayList<>();
        for (int i = 0; i < relations.size() && i < relationEmbedResults.size(); i++) {
            Relation newRelation = relations.get(i);
            List<String> lhsRhs = new ArrayList<>();
            Object lhs = newRelation.getLhs();
            Object rhs = newRelation.getRhs();
            lhsRhs.add(lhs instanceof String s
                    ? s
                    : (((Entity) lhs).getContent() != null && !((Entity) lhs).getContent().isBlank()
                            ? ((Entity) lhs).getUuid()
                            : null));
            lhsRhs.add(rhs instanceof String s
                    ? s
                    : (((Entity) rhs).getContent() != null && !((Entity) rhs).getContent().isBlank()
                            ? ((Entity) rhs).getUuid()
                            : null));
            if (lhsRhs.contains(null)) {
                continue;
            }
            List<Map<String, Object>> result = dbBackend.search(
                    newRelation.getContent(),
                    state.getStrategy().getRecallRelation().getTopK(),
                    GraphConstants.RELATION_COLLECTION,
                    state.getStrategy().getRecallRelation().getRankConfig(),
                    0,
                    0,
                    QueryExpressions.inList("lhs", lhsRhs)
                            .and(QueryExpressions.inList("rhs", lhsRhs))
                            .and(QueryExpressions.filterUser(userId)),
                    null,
                    relationEmbedResults.get(i),
                    Map.of("language", state.getPrompting().getLanguage())
            ).getOrDefault(GraphConstants.RELATION_COLLECTION, List.of());
            List<Relation> currentRelations = new ArrayList<>();
            for (Map<String, Object> item : result) {
                Relation relation = mapToRelation(item);
                state.getRetrievedRelations().put(relation.getUuid(), relation);
                currentRelations.add(relation);
            }
            if (!currentRelations.isEmpty()) {
                List<Entity> existingEntities = List.of(
                        endpointToEntity(newRelation.getLhs(), state),
                        endpointToEntity(newRelation.getRhs(), state));
                CompletableFuture<AssistantMessage> task = CompletableFuture.supplyAsync(() -> {
                    try {
                        return invokeLlm(
                                ExtractionPrompts.dedupeRelationList(
                                        content,
                                        newRelation,
                                        currentRelations,
                                        existingEntities,
                                        state.getHistory(),
                                        null,
                                        state.getPrompting().getLanguage(),
                                        2
                                ),
                                Map.of()
                        );
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                });
                dedupeTasks.add(new PostprocessGraphObjects.RelationTask(newRelation,
                        currentRelations.stream().map(Relation::toMap).toList(), task));
            }
        }
        PostprocessGraphObjects.parseRelationUuidsToRemove(dedupeTasks, state);
    }

    private Entity endpointToEntity(Object endpoint, States.GraphMemState state) {
        if (endpoint instanceof Entity entity) {
            return entity;
        }
        Entity entity = state.getLookupTable().getEntities().get(String.valueOf(endpoint));
        if (entity == null) {
            throw new IllegalArgumentException(
                    "The entity UUID " + endpoint
                            + " is not present in lookup table while building relation dedupe prompts.");
        }
        return entity;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeRelationList(Object parsed) {
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> normalizedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalizedMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            Object extracted = firstPresent(
                    normalizedMap,
                    List.of("extracted_relations", "extractedRelations"));
            if (extracted instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        Map<String, Object> normalizedItem = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                            normalizedItem.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                        result.add(normalizedItem);
                    }
                }
                return result;
            }
        }
        if (parsed instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    Map<String, Object> normalizedItem = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                        normalizedItem.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(normalizedItem);
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeDeclarationList(Object parsed) {
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> normalizedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalizedMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            Object extracted = firstPresent(
                    normalizedMap,
                    List.of("extracted_entities", "extractedEntities"));
            if (extracted instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        Map<String, Object> normalizedItem = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                            normalizedItem.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                        result.add(normalizedItem);
                    }
                }
                return result;
            }
        }
        if (parsed instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    Map<String, Object> normalizedItem = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                        normalizedItem.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(normalizedItem);
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeDuplicationList(Object parsed) {
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> normalizedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalizedMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            Object extracted = firstPresent(
                    normalizedMap,
                    List.of("duplicated_entities", "duplicatedEntities"));
            if (extracted instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        Map<String, Object> normalizedItem = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                            normalizedItem.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                        result.add(normalizedItem);
                    }
                }
                return result;
            }
        }
        if (parsed instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    Map<String, Object> normalizedItem = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                        normalizedItem.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(normalizedItem);
                }
            }
            return result;
        }
        return List.of();
    }

    private Object firstPresent(Map<String, Object> source, List<String> keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private record EntityDeclarationResult(boolean existingEntityMissing, List<EntityDeclaration> entities) {
    }
}
